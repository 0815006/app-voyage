package com.voyage.controller;

import com.voyage.common.BusinessException;
import com.voyage.common.EmpContext;
import com.voyage.common.Result;
import com.voyage.entity.SessionTextAnalysis;
import com.voyage.entity.WorkspaceFile;
import com.voyage.entity.WorkspaceSubDir;
import com.voyage.fileanalysis.FileAnalysisWorkbench;
import com.voyage.service.SessionTextAnalysisService;
import com.voyage.service.WorkspaceFileService;
import com.voyage.service.WorkspaceSubDirService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 文件分析（Document Studio）Controller。
 * <p>
 * 依据 PRD 5.1 / 5.2 提供：
 * <ul>
 *   <li>工作区管理：GET /api/workspace/list、POST /api/workspace/create、DELETE /api/workspace/{subDirId}</li>
 *   <li>工作区文件树：GET /api/workspace/{subDirId}/files、POST .../file/upload</li>
 *   <li>文件单点操作：GET .../file/{fileId}/preview（JSON 预览，可加 raw=1 取原始流）、GET .../download、DELETE .../file/{fileId}</li>
 *   <li>SSE 文件分析对话：POST /api/session/text-analysis/chat（委派 FileAnalysisWorkbench）</li>
 * </ul>
 * 所有接口均从 {@link EmpContext} 读取当前操作员工号，服务层再做 7 位工号归属隔离与沙箱路径校验。
 *
 * @author voyage
 */
@Slf4j
@RestController
@RequestMapping("/api")
public class FileAnalysisController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final WorkspaceSubDirService workspaceService;
    private final WorkspaceFileService fileService;
    private final SessionTextAnalysisService sessionService;
    private final FileAnalysisWorkbench workbench;

    public FileAnalysisController(WorkspaceSubDirService workspaceService,
                                  WorkspaceFileService fileService,
                                  SessionTextAnalysisService sessionService,
                                  FileAnalysisWorkbench workbench) {
        this.workspaceService = workspaceService;
        this.fileService = fileService;
        this.sessionService = sessionService;
        this.workbench = workbench;
    }

    // ==================== 工作区管理 ====================

    /** 查询当前操作者的历史工作区列表（按更新时间倒序）。 */
    @GetMapping("/workspace/list")
    public Result<List<WorkspaceSubDir>> listWorkspaces() {
        return Result.ok(workspaceService.listByEmpNo(EmpContext.getEmpNo()));
    }

    /** 手动/自动新建工作区（物理创建 UUID 隔离子目录并落库元数据）。 */
    @PostMapping("/workspace/create")
    public Result<WorkspaceSubDir> createWorkspace(@RequestBody(required = false) Map<String, String> body) {
        String dirName = body == null ? "" : body.getOrDefault("dirName", "");
        return Result.ok(workspaceService.create(EmpContext.getEmpNo(), dirName));
    }

    /** 删除工作区（物理递归删除子目录 + 数据库级联清理），仅限归属当前操作者。 */
    @DeleteMapping("/workspace/{subDirId}")
    public Result<Void> deleteWorkspace(@PathVariable("subDirId") String subDirId) {
        workspaceService.delete(EmpContext.getEmpNo(), subDirId);
        return Result.ok();
    }

    // ==================== 工作区文件 ====================

    /** 查询指定工作区当前会话（含 context_messages 历史，供切换工作区后回显）。 */
    @GetMapping("/workspace/{subDirId}/session")
    public Result<SessionTextAnalysis> getSubDirSession(@PathVariable("subDirId") String subDirId) {
        // 校验归属：工作区必须归属当前操作者（文件列表做了校验，此处按同一权限口径执行）
        fileService.listBySubDir(EmpContext.getEmpNo(), subDirId);
        return Result.ok(sessionService.listBySubDir(subDirId).stream().findFirst().orElse(null));
    }

    /** 获取指定工作区的文件树列表（轻量元数据，不含解析大文本）。 */
    @GetMapping("/workspace/{subDirId}/files")
    public Result<List<FileItem>> listFiles(@PathVariable("subDirId") String subDirId) {
        List<WorkspaceFile> metas = fileService.listBySubDir(EmpContext.getEmpNo(), subDirId);
        return Result.ok(metas.stream().map(FileItem::from).toList());
    }

    /** 上传文件到工作区（存盘 + ai-tool-doc 预解析 Markdown/Schema 落库）。 */
    @PostMapping("/workspace/{subDirId}/file/upload")
    public Result<WorkspaceFile> uploadFile(@PathVariable("subDirId") String subDirId,
                                            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "上传文件不能为空");
        }
        return Result.ok(fileService.upload(EmpContext.getEmpNo(), subDirId, file));
    }

    /** 预览文件：默认返回 JSON 内容视图（含 parsed_markdown / parsed_schema）；raw=1 时输出原始文件内联流（PDF/图片等）。 */
    @GetMapping("/workspace/file/{fileId}/preview")
    public Object previewFile(@PathVariable("fileId") String fileId,
                              @RequestParam(value = "raw", defaultValue = "false") boolean raw,
                              HttpServletResponse response) throws IOException {
        String empNo = EmpContext.getEmpNo();
        WorkspaceFile meta = fileService.previewMeta(empNo, fileId);
        if (raw) {
            streamFile(meta, response, false);
            return null;
        }
        return Result.ok(meta);
    }

    /** 下载原文件或 Agent 渲染生成的报告（附件下载）。 */
    @GetMapping("/workspace/file/{fileId}/download")
    public void downloadFile(@PathVariable("fileId") String fileId,
                             HttpServletResponse response) throws IOException {
        WorkspaceFile meta = fileService.previewMeta(EmpContext.getEmpNo(), fileId);
        streamFile(meta, response, true);
    }

    /** 删除文件（同步清理物理磁盘与数据库记录）。 */
    @DeleteMapping("/workspace/file/{fileId}")
    public Result<Void> deleteFile(@PathVariable("fileId") String fileId) {
        fileService.deleteFile(EmpContext.getEmpNo(), fileId);
        return Result.ok();
    }

    // ==================== SSE 文件分析对话 ====================

    /**
     * 发起一轮文件分析对话（SSE 流式，调用 ai-tool-doc 质检/检索/生成）。
     *
     * @param body 需包含 subDirId（激活工作区）与 prompt（用户指令）
     */
    @PostMapping(value = "/session/text-analysis/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody(required = false) Map<String, String> body) {
        String subDirId = body == null ? "" : body.getOrDefault("subDirId", "");
        String prompt = body == null ? "" : body.getOrDefault("prompt", "");
        if (subDirId.isBlank() || prompt.isBlank()) {
            throw new BusinessException(400, "subDirId 与 prompt 不能为空");
        }
        String empNo = EmpContext.getEmpNo();
        log.info("文件分析 chat: empNo={}, subDirId={}, promptLen={}", empNo, subDirId, prompt.length());
        return workbench.run(empNo, subDirId, prompt);
    }

    // ==================== 内部工具 ====================

    /** 以安全物理路径向响应输出原始文件流（download=附件，preview 内联）。 */
    private void streamFile(WorkspaceFile meta, HttpServletResponse response, boolean attachment) throws IOException {
        Path path = fileService.resolvePhysicalPath(meta);
        if (!Files.isRegularFile(path)) {
            throw new BusinessException(404, "物理文件缺失: " + meta.getFileName());
        }
        String encoded = URLEncoder.encode(meta.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType(contentTypeOf(meta.getFileName()));
        response.setHeader("Content-Disposition",
                (attachment ? "attachment" : "inline") + "; filename*=UTF-8''" + encoded);
        response.setContentLengthLong(Files.size(path));
        try (InputStream is = Files.newInputStream(path);
             OutputStream os = response.getOutputStream()) {
            is.transferTo(os);
            os.flush();
        }
    }

    /** 依据扩展名推断 Content-Type（未知一律 octet-stream）。 */
    private String contentTypeOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot + 1).toUpperCase(Locale.ROOT) : "";
        return switch (ext) {
            case "PDF" -> "application/pdf";
            case "PNG" -> "image/png";
            case "JPG", "JPEG" -> "image/jpeg";
            case "GIF" -> "image/gif";
            case "SVG" -> "image/svg+xml";
            case "DOCX" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "JSON" -> "application/json; charset=utf-8";
            case "TXT", "MD", "MARKDOWN" -> "text/plain; charset=utf-8";
            default -> "application/octet-stream";
        };
    }

    /** 文件树轻量条目（避免列表接口传输解析大文本）。 */
    @Data
    public static class FileItem {
        private String fileId;
        private String fileName;
        private String fileType;
        private Long fileSize;
        private LocalDateTime createTime;
        private String createTimeText;

        static FileItem from(WorkspaceFile f) {
            FileItem item = new FileItem();
            item.setFileId(f.getFileId());
            item.setFileName(f.getFileName());
            item.setFileType(f.getFileType());
            item.setFileSize(f.getFileSize());
            item.setCreateTime(f.getCreateTime());
            item.setCreateTimeText(f.getCreateTime() == null ? "" : TS.format(f.getCreateTime()));
            return item;
        }
    }
}

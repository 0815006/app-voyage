package com.voyage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realapex.tool.base.PathSafety;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.doc.config.DocToolConfig;
import com.realapex.tool.doc.config.DocToolFactory;
import com.realapex.tool.doc.model.DocConvertRequest;
import com.realapex.tool.doc.model.DocConvertResult;
import com.realapex.tool.doc.model.TemplateSchema;
import com.realapex.tool.doc.model.TemplateSchemaRequest;
import com.realapex.tool.doc.tool.InspectTemplateSchemaTool;
import com.realapex.tool.doc.tool.ReadAndConvertDocTool;
import com.voyage.common.BusinessException;
import com.voyage.entity.WorkspaceFile;
import com.voyage.entity.WorkspaceSubDir;
import com.voyage.mapper.WorkspaceFileMapper;
import com.voyage.service.WorkspaceFileService;
import com.voyage.service.WorkspaceSubDirService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 文件分析工作区文件 Service 实现（磁盘文件 + 数据库元数据原子同步）。
 * <p>
 * 上传即通过 ai-tool-doc（read_and_convert_doc / inspect_template_schema）
 * 预解析出 Markdown / Schema 落库，满足「轻量化存储 + 与 AI 视角一致」的预览需求。
 */
@Slf4j
@Service
public class WorkspaceFileServiceImpl
        extends ServiceImpl<WorkspaceFileMapper, WorkspaceFile>
        implements WorkspaceFileService {

    /** 允许上传的文件扩展名（大写）。 */
    private static final Set<String> ALLOWED_EXT = Set.of(
            "DOCX", "DOC", "PDF", "TXT", "MD", "MARKDOWN", "JSON", "PNG", "JPG", "JPEG",
            "XLSX", "XLS");

    /** 可用 ai-tool-doc 解析为 Markdown 的扩展名。 */
    private static final Set<String> PARSEABLE_DOC_EXT = Set.of(
            "DOCX", "DOC", "PDF", "XLSX", "XLS");

    /** Agent 渲染/生成后可注册为工作区文件的扩展名（可下载产物）。 */
    private static final Set<String> GENERATED_EXT = Set.of(
            "DOCX", "DOC", "PDF", "XLSX", "XLS", "TXT", "MD", "MARKDOWN", "JSON");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final WorkspaceSubDirService workspaceService;

    public WorkspaceFileServiceImpl(WorkspaceSubDirService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @Override
    public List<WorkspaceFile> listBySubDir(String empNo, String subDirId) {
        assertOwned(empNo, subDirId);
        return list(new LambdaQueryWrapper<WorkspaceFile>()
                .eq(WorkspaceFile::getSubDirId, subDirId)
                .orderByDesc(WorkspaceFile::getCreateTime));
    }

    @Override
    @Transactional
    public WorkspaceFile upload(String empNo, String subDirId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "文件不能为空");
        }
        WorkspaceSubDir ws = assertOwned(empNo, subDirId);

        // 1. 文件名净化：仅取 baseName，阻断路径穿越
        String originalName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        String fileName = Path.of(originalName).getFileName().toString();
        String ext = extOf(fileName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BusinessException(400, "暂不支持的文件格式: " + ext + "（支持 docx/doc/pdf/txt/md/json/png 等）");
        }

        // 2. 写入物理磁盘（沙箱安全路径）
        Path subDirAbs = Path.of(ws.getAbsPath()).toAbsolutePath().normalize();
        Path target;
        try {
            Files.createDirectories(subDirAbs);
            target = PathSafety.resolveSafePath(subDirAbs, fileName);
            file.transferTo(target);
        } catch (IOException e) {
            log.error("上传文件写入失败: {}/{}", ws.getAbsPath(), fileName, e);
            throw new BusinessException("文件写入失败: " + e.getMessage());
        }

        // 3. 落库元数据（轻量化：不存 Blob）
        WorkspaceFile meta = new WorkspaceFile();
        meta.setFileId(UUID.randomUUID().toString());
        meta.setSubDirId(subDirId);
        meta.setFileName(fileName);
        meta.setFileType(ext);
        try {
            meta.setFileSize(Files.size(target));
        } catch (IOException e) {
            meta.setFileSize(0L);
        }
        save(meta);
        log.info("文件分析上传完成: fileId={}, ws={}, name={}, size={}",
                meta.getFileId(), subDirId, fileName, meta.getFileSize());

        // 4. ai-tool-doc 预解析 Markdown / Schema（失败不阻断上传，仅留空）
        parseAndFill(meta, ws, target, ext);
        updateById(meta);
        return getById(meta.getFileId());
    }

    @Override
    public WorkspaceFile previewMeta(String empNo, String fileId) {
        WorkspaceFile meta = requireOwnedFile(empNo, fileId);
        return meta;
    }

    @Override
    public Path resolvePhysicalPath(WorkspaceFile file) {
        WorkspaceSubDir ws = workspaceService.getById(file.getSubDirId());
        Path subDirAbs = Path.of(ws.getAbsPath()).toAbsolutePath().normalize();
        // 文件名净化 + 沙箱内安全解析（阻断 ../）
        String cleanName = Path.of(file.getFileName()).getFileName().toString();
        return PathSafety.resolveSafePath(subDirAbs, cleanName);
    }

    @Override
    @Transactional
    public void deleteFile(String empNo, String fileId) {
        WorkspaceFile meta = requireOwnedFile(empNo, fileId);
        // 1. 原子清理物理磁盘文件
        try {
            Files.deleteIfExists(resolvePhysicalPath(meta));
        } catch (IOException e) {
            log.warn("物理文件删除失败(不影响元数据删除): {} -> {}", meta.getFileName(), e.getMessage());
        }
        // 2. 删除数据库记录（doc_vector 由外键级联清理）
        removeById(fileId);
        log.info("文件分析删除文件: fileId={}, name={}", fileId, meta.getFileName());
    }

    @Override
    @Transactional
    public List<WorkspaceFile> syncGeneratedFiles(String empNo, String subDirId, long afterEpochMillis) {
        WorkspaceSubDir ws = assertOwned(empNo, subDirId);
        Path baseDir = Path.of(ws.getAbsPath()).toAbsolutePath().normalize();
        if (!Files.isDirectory(baseDir)) {
            return List.of();
        }

        // 已有文件名集合，避免重复注册
        Set<String> existingNames = new HashSet<>(list(new LambdaQueryWrapper<WorkspaceFile>()
                .eq(WorkspaceFile::getSubDirId, subDirId)
                .select(WorkspaceFile::getFileName))
                .stream()
                .map(WorkspaceFile::getFileName)
                .toList());

        List<WorkspaceFile> created = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(baseDir)) {
            walk.filter(Files::isRegularFile)
                    // 跳过 SDK 临时落盘目录（URL/Base64 下载）
                    .filter(p -> !p.startsWith(baseDir.resolve("temp")))
                    .forEach(p -> {
                        try {
                            if (Files.getLastModifiedTime(p).toMillis() < afterEpochMillis) {
                                return;
                            }
                            String name = p.getFileName().toString();
                            String ext = extOf(name);
                            if (!GENERATED_EXT.contains(ext) || existingNames.contains(name)) {
                                return;
                            }
                            WorkspaceFile meta = new WorkspaceFile();
                            meta.setFileId(UUID.randomUUID().toString());
                            meta.setSubDirId(subDirId);
                            meta.setFileName(name);
                            meta.setFileType(ext);
                            meta.setFileSize(Files.size(p));
                            // 渲染产物按轻量化策略暂不做二次 ai-tool-doc 解析，留空由下载/预览兜底
                            save(meta);
                            existingNames.add(name);
                            created.add(meta);
                            log.info("同步 Agent 渲染产物为工作区文件: subDirId={}, name={}", subDirId, name);
                        } catch (IOException e) {
                            log.debug("同步渲染产物跳过: {} -> {}", p, e.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.warn("同步 Agent 渲染产物目录扫描失败: {}", e.getMessage());
        }
        return created;
    }

    // ==================== 内部逻辑 ====================

    /** 归属校验：工作区必须存在且属于当前操作者。 */
    private WorkspaceSubDir assertOwned(String empNo, String subDirId) {
        WorkspaceSubDir ws = workspaceService.getById(subDirId);
        if (!ws.getEmpNo().equals(empNo)) {
            throw new BusinessException(403, "无权访问该工作区");
        }
        return ws;
    }

    /** 归属校验后取文件元数据。 */
    private WorkspaceFile requireOwnedFile(String empNo, String fileId) {
        WorkspaceFile meta = getById(fileId);
        if (meta == null) {
            throw new BusinessException(404, "文件不存在");
        }
        assertOwned(empNo, meta.getSubDirId());
        return meta;
    }

    /** ai-tool-doc 预解析：docx/doc/pdf/xlsx/xls -> Markdown；txt/md/json 直读；png 等跳过。 */
    private void parseAndFill(WorkspaceFile meta, WorkspaceSubDir ws, Path target, String ext) {
        try {
            if (PARSEABLE_DOC_EXT.contains(ext)) {
                parseDocViaTool(meta, ws, target, ext);
            } else if ("TXT".equals(ext) || "MD".equals(ext) || "MARKDOWN".equals(ext) || "JSON".equals(ext)) {
                meta.setParsedMarkdown(Files.readString(target, StandardCharsets.UTF_8));
            } else {
                meta.setParsedMarkdown(null); // 图片等不支持转 Markdown
            }
        } catch (Exception e) {
            log.warn("ai-tool-doc 预解析失败(上传不阻断): fileId={} name={} -> {}",
                    meta.getFileId(), meta.getFileName(), e.getMessage());
            meta.setParsedMarkdown(null);
        }
    }

    /** 使用 ai-tool-doc 工具解析为 Markdown，并尝试探查模板 Schema（.docx/.xlsx 含占位符时）。 */
    private void parseDocViaTool(WorkspaceFile meta, WorkspaceSubDir ws, Path target, String ext) throws Exception {
        // 沙箱 baseDir 指向当前工作区物理目录（工具内部 PathSafety 二次校验）
        DocToolConfig config = DocToolConfig.builder()
                .baseDir(Path.of(ws.getAbsPath()).toAbsolutePath().normalize())
                .maxDocSizeBytes(100 * 1024 * 1024L)
                .build();

        // 通过 DocToolFactory 创建整套 ai-tool-doc 工具（内部装配 SchemaInspector / Renderer）
        ReadAndConvertDocTool convertTool = null;
        InspectTemplateSchemaTool schemaTool = null;
        for (AgentTool<?, ?> tool : DocToolFactory.createDocTools(config)) {
            if (tool instanceof ReadAndConvertDocTool c) {
                convertTool = c;
            } else if (tool instanceof InspectTemplateSchemaTool s) {
                schemaTool = s;
            }
        }
        if (convertTool == null) {
            throw new IllegalStateException("ai-tool-doc 工具组缺失 read_and_convert_doc");
        }

        // 1. 转换为 Markdown
        DocConvertResult result = convertTool.execute(
                new DocConvertRequest(meta.getFileName(), ext.toLowerCase(Locale.ROOT), null, null));
        if (result != null && result.getMarkdown() != null) {
            meta.setParsedMarkdown(result.getMarkdown());
        }

        // 2. 模板 Schema 探查：仅 word/excel 模板可能含 {{占位符}}
        if (schemaTool != null && ("DOCX".equals(ext) || "XLSX".equals(ext))) {
            try {
                TemplateSchema schema = schemaTool.execute(new TemplateSchemaRequest(meta.getFileName()));
                if (schema != null) {
                    meta.setParsedSchema(OBJECT_MAPPER.writeValueAsString(schema));
                }
            } catch (Exception e) {
                log.debug("模板 Schema 探查跳过(非模板或解析失败): {} -> {}", meta.getFileName(), e.getMessage());
                meta.setParsedSchema(null);
            }
        }
    }

    private String extOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot + 1).toUpperCase(Locale.ROOT) : "";
    }
}

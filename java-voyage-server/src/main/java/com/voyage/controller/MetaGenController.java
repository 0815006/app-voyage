package com.voyage.controller;

import com.voyage.common.EmpContext;
import com.voyage.common.Result;
import com.voyage.entity.MetaEntityFile;
import com.voyage.entity.MetaFtpConfig;
import com.voyage.service.MetaEntityFileService;
import com.voyage.service.MetaFtpConfigService;
import com.voyage.service.MetaGenEngineService;
import com.voyage.service.MetaSequenceTrackerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.List;

@RestController
@Slf4j
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaGenController {

    private final MetaGenEngineService engineService;
    private final MetaEntityFileService entityFileService;
    private final MetaSequenceTrackerService seqService;
    private final MetaFtpConfigService ftpConfigService;

    // ===================== 执行与生成 =====================

    /**
     * 实时预览生成（同步，3行，不更新 tracker）
     */
    @PostMapping("/execute/preview/{modelId}")
    public Result<String> preview(@PathVariable Long modelId) {
        String empNo = EmpContext.getEmpNo();
        try {
            String previewText = engineService.preview(modelId, empNo);
            return Result.ok(previewText);
        } catch (Exception e) {
            log.error("预览生成失败", e);
            return Result.fail("预览失败: " + e.getMessage());
        }
    }

    /**
     * 批量生成任务（异步）
     */
    @PostMapping("/execute/generate")
    public Result<MetaEntityFile> generate(@RequestBody GenerateRequest req) {
        String empNo = EmpContext.getEmpNo();
        try {
            MetaEntityFile record = engineService.generateAsync(req.getModelId(), req.getRowCount(), req.getBatchName(), empNo);
            return Result.ok(record);
        } catch (Exception e) {
            log.error("批量生成失败", e);
            return Result.fail("生成失败: " + e.getMessage());
        }
    }

    /**
     * 查询生成进度
     */
    @GetMapping("/execute/status/{taskId}")
    public Result<MetaEntityFile> getStatus(@PathVariable Long taskId) {
        MetaEntityFile record = engineService.getStatus(taskId);
        if (record == null) return Result.fail("任务不存在");
        return Result.ok(record);
    }

    /**
     * 获取生成历史
     */
    @GetMapping("/execute/history")
    public Result<List<MetaEntityFile>> getHistory(@RequestParam Long modelId) {
        List<MetaEntityFile> list = entityFileService.listByModelId(modelId);
        return Result.ok(list);
    }

    /**
     * 下载实体文件
     */
    @GetMapping("/execute/download/{fileId}")
    public ResponseEntity<Resource> download(@PathVariable Long fileId) {
        MetaEntityFile record = entityFileService.getById(fileId);
        if (record == null) return ResponseEntity.notFound().build();
        String filePath = record.getStoragePath();
        if (filePath == null || filePath.isEmpty()) return ResponseEntity.notFound().build();

        File file = new File(filePath);
        if (!file.exists()) return ResponseEntity.notFound().build();

        Resource resource = new FileSystemResource(file);
        String downloadName = record.getFileName() != null ? record.getFileName() : file.getName();
        String encodedName;
        try {
            encodedName = URLEncoder.encode(downloadName, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            encodedName = downloadName;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedName)
                .body(resource);
    }

    /**
     * 删除生成记录及物理文件
     */
    @DeleteMapping("/execute/file/{fileId}")
    public Result<String> deleteFile(@PathVariable Long fileId) {
        String empNo = EmpContext.getEmpNo();
        engineService.deleteFile(fileId, empNo);
        return Result.ok("删除成功");
    }

    /**
     * 上传文件到 FTP 服务器
     */
    @PostMapping("/execute/upload-ftp")
    public Result<String> uploadToFtp(@RequestBody UploadFtpRequest req) {
        MetaEntityFile record = entityFileService.getById(req.getFileId());
        if (record == null) return Result.fail("文件记录不存在");
        String filePath = record.getStoragePath();
        if (filePath == null || filePath.isEmpty()) return Result.fail("文件路径为空");
        File localFile = new File(filePath);
        if (!localFile.exists()) return Result.fail("本地文件不存在");

        MetaFtpConfig ftpConfig = ftpConfigService.getById(req.getFtpConfigId());
        if (ftpConfig == null) return Result.fail("FTP配置不存在");

        FTPClient ftpClient = new FTPClient();
        try (FileInputStream fis = new FileInputStream(localFile)) {
            int port = ftpConfig.getFtpPort() != null ? ftpConfig.getFtpPort() : 21;
            ftpClient.connect(ftpConfig.getFtpIp(), port);
            if (!ftpClient.login(ftpConfig.getUsername(), ftpConfig.getPassword())) {
                return Result.fail("FTP登录失败，请检查用户名密码");
            }
            ftpClient.setFileType(FTP.BINARY_FILE_TYPE);
            ftpClient.enterLocalPassiveMode();
            ftpClient.setBufferSize(1024 * 1024);

            String remotePath = ftpConfig.getRemotePath();
            if (remotePath != null && !remotePath.isEmpty()) {
                makeDirs(ftpClient, remotePath);
                ftpClient.changeWorkingDirectory(remotePath);
            }

            String remoteFileName = record.getFileName() != null ? record.getFileName() : localFile.getName();
            boolean uploaded = ftpClient.storeFile(remoteFileName, fis);
            if (!uploaded) {
                return Result.fail("FTP上传失败: " + ftpClient.getReplyString());
            }
            log.info("FTP upload success: {} -> {}:{}{}/{}", filePath, ftpConfig.getFtpIp(), port, remotePath, remoteFileName);
            return Result.ok("上传成功");
        } catch (IOException e) {
            log.error("FTP上传异常", e);
            return Result.fail("FTP上传异常: " + e.getMessage());
        } finally {
            try {
                if (ftpClient.isConnected()) {
                    ftpClient.logout();
                    ftpClient.disconnect();
                }
            } catch (IOException ignored) {}
        }
    }

    private void makeDirs(FTPClient ftpClient, String path) throws IOException {
        String[] dirs = path.replace('\\', '/').split("/");
        for (String dir : dirs) {
            if (dir.isEmpty()) continue;
            if (!ftpClient.changeWorkingDirectory(dir)) {
                ftpClient.makeDirectory(dir);
                ftpClient.changeWorkingDirectory(dir);
            }
        }
    }

    // ===================== 系统辅助 =====================

    /**
     * 重置序列号
     */
    @PostMapping("/sys/sequence/reset")
    public Result<String> resetSequence(@RequestBody ResetSeqRequest req) {
        seqService.reset("SEQ", req.getTargetId());
        return Result.ok("重置成功");
    }

    /**
     * 手动清理临时文件
     */
    @PostMapping("/sys/clean-temp")
    public Result<String> cleanTemp() {
        // 简单实现：清理预览目录
        File previewDir = new File("./storage/preview");
        if (previewDir.exists() && previewDir.isDirectory()) {
            File[] files = previewDir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
        }
        return Result.ok("清理完成");
    }

    // ===================== DTO 内部类 =====================

    public static class GenerateRequest {
        private Long modelId;
        private Integer rowCount;
        private String batchName;

        public Long getModelId() { return modelId; }
        public void setModelId(Long modelId) { this.modelId = modelId; }
        public Integer getRowCount() { return rowCount; }
        public void setRowCount(Integer rowCount) { this.rowCount = rowCount; }
        public String getBatchName() { return batchName; }
        public void setBatchName(String batchName) { this.batchName = batchName; }
    }

    public static class ResetSeqRequest {
        private String targetId;
        public String getTargetId() { return targetId; }
        public void setTargetId(String targetId) { this.targetId = targetId; }
    }

    public static class UploadFtpRequest {
        private Long fileId;
        private Long ftpConfigId;

        public Long getFileId() { return fileId; }
        public void setFileId(Long fileId) { this.fileId = fileId; }
        public Long getFtpConfigId() { return ftpConfigId; }
        public void setFtpConfigId(Long ftpConfigId) { this.ftpConfigId = ftpConfigId; }
    }
}

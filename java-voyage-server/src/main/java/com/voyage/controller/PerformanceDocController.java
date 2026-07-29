package com.voyage.controller;

import com.voyage.common.Result;
import com.voyage.service.PerformanceDocService;
import com.voyage.service.PerformanceDocService.DocFileInfo;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/performance/doc")
@Slf4j
@RequiredArgsConstructor
public class PerformanceDocController {

    @Value("${performance.doc-path}")
    private String docPath;

    private final PerformanceDocService docService;

    @PostMapping("/generate")
    public Result<String> generate(@RequestParam Long taskId) {
        try {
            String filename = docService.generateDoc(taskId);
            return Result.ok(filename);
        } catch (Exception e) {
            log.error("方案文档生成失败, taskId={}", taskId, e);
            return Result.fail("文档生成失败：" + e.getMessage());
        }
    }

    @GetMapping("/list")
    public Result<List<DocFileInfo>> list() {
        return Result.ok(docService.listDocs());
    }

    @DeleteMapping("/delete")
    public Result<String> delete(@RequestBody Map<String, String> body) {
        String fileName = body.get("fileName");
        if (fileName == null || fileName.isEmpty()) {
            return Result.fail("文件名不能为空");
        }
        if (docService.deleteDoc(fileName)) {
            return Result.ok("删除成功");
        }
        return Result.fail("文件不存在");
    }

    @GetMapping("/download")
    public void download(@RequestParam String fileName, HttpServletResponse response) {
        File file = new File(docPath + fileName);
        if (!file.exists() || !file.isFile()) {
            return;
        }

        try (InputStream is = new FileInputStream(file);
             OutputStream os = response.getOutputStream()) {
            response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            response.setHeader("Content-Disposition",
                    "attachment;filename=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
            byte[] buffer = new byte[1024];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.flush();
        } catch (IOException e) {
            log.error("方案文档下载失败", e);
        }
    }
}

package com.voyage.controller;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.voyage.common.EmpContext;
import com.voyage.common.Result;
import com.voyage.entity.*;
import com.voyage.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@Slf4j
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaManagerController {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final MetaFileModelService modelService;
    private final MetaFieldDefinitionService fieldService;
    private final MetaEnumLibraryService enumService;
    private final MetaRefFileService refFileService;
    private final MetaGenEngineService engineService;
    private final MetaFtpConfigService ftpConfigService;

    @Value("${meta.template-path}")
    private String templatePath;

    @Value("${meta.ref-file-path}")
    private String refFilePath;

    // ===================== 模型管理 =====================

    @GetMapping("/models")
    public Result<IPage<MetaFileModel>> listModels(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        String empNo = EmpContext.getEmpNo();
        List<MetaFileModel> list = modelService.listByUser(empNo);
        // 手动分页
        int total = list.size();
        int from = (page - 1) * size;
        int to = Math.min(from + size, total);
        IPage<MetaFileModel> result = new Page<>(page, size, total);
        if (from < total) {
            result.setRecords(list.subList(from, to));
        }
        return Result.ok(result);
    }

    @PostMapping("/models")
    public Result<MetaFileModel> createModel(@RequestBody MetaFileModel model) {
        String empNo = EmpContext.getEmpNo();
        modelService.saveModel(model, empNo);
        return Result.ok(model);
    }

    @GetMapping("/models/{id}")
    public Result<ModelDetail> getModelDetail(@PathVariable Long id) {
        MetaFileModel model = modelService.getById(id);
        if (model == null) return Result.fail("模型不存在");
        List<MetaFieldDefinition> fields = fieldService.listByModelId(id);
        ModelDetail detail = new ModelDetail();
        detail.setModel(model);
        detail.setFields(fields);
        return Result.ok(detail);
    }

    @PutMapping("/models/{id}")
    public Result<String> updateModel(@PathVariable Long id, @RequestBody MetaFileModel model) {
        String empNo = EmpContext.getEmpNo();
        model.setId(id);
        modelService.updateModel(model, empNo);
        return Result.ok("更新成功");
    }

    @DeleteMapping("/models/{id}")
    public Result<String> deleteModel(@PathVariable Long id) {
        String empNo = EmpContext.getEmpNo();
        MetaFileModel model = modelService.getById(id);
        if (model == null) return Result.fail("模型不存在");
        if (!model.getOwnerId().equals(empNo)) return Result.fail("无权删除");
        // 删除关联字段
        fieldService.lambdaUpdate().eq(MetaFieldDefinition::getModelId, id).remove();
        modelService.removeById(id);
        return Result.ok("删除成功");
    }

    @PostMapping("/models/{id}/publish")
    public Result<String> publishModel(@PathVariable Long id) {
        String empNo = EmpContext.getEmpNo();
        // 发布前校验字段
        List<MetaFieldDefinition> fields = fieldService.listByModelId(id);
        List<String> errors = fieldService.validateFields(id, fields);
        if (!errors.isEmpty()) {
            return Result.fail("字段校验失败: " + String.join("; ", errors));
        }
        modelService.publish(id, empNo);
        return Result.ok("发布成功");
    }

    @PostMapping("/models/{id}/share")
    public Result<String> shareModel(@PathVariable Long id, @RequestBody ShareRequest req) {
        String empNo = EmpContext.getEmpNo();
        modelService.share(id, req.getSharedWith(), empNo);
        return Result.ok("共享成功");
    }

    // ===================== 字段管理 =====================

    @PostMapping("/models/{modelId}/fields")
    public Result<String> saveFields(@PathVariable Long modelId, @RequestBody List<MetaFieldDefinition> fields,
                                      @RequestParam(value = "section", required = false) String section) {
        String empNo = EmpContext.getEmpNo();
        // 校验模型归属
        MetaFileModel model = modelService.getById(modelId);
        if (model == null) return Result.fail("模型不存在");
        if ("PUBLISHED".equals(model.getStatus())) return Result.fail("已发布模型请先退回草稿");

        // 如果前端传递了 section 参数，确保每个字段都有正确的 section 值
        if (section != null && !section.isEmpty() && fields != null) {
            for (MetaFieldDefinition field : fields) {
                field.setSection(section);
            }
        }

        fieldService.batchSave(modelId, fields);
        return Result.ok("保存成功");
    }

    @PostMapping("/fields/validate")
    public Result<List<String>> validateFields(@RequestBody ValidateRequest req) {
        List<String> errors = fieldService.validateFields(req.getModelId(), req.getFields());
        return Result.ok(errors);
    }

    @GetMapping("/fields/sum-targets/{modelId}")
    public Result<List<MetaFieldDefinition>> getSumTargets(@PathVariable Long modelId) {
        return Result.ok(fieldService.getSumTargets(modelId));
    }

    // ===================== 枚举库管理 =====================

    @GetMapping("/enums")
    public Result<List<MetaEnumLibrary>> listEnums() {
        return Result.ok(enumService.listAll());
    }

    @GetMapping("/enums/keys")
    public Result<List<String>> getEnumKeys() {
        return Result.ok(enumService.getAllKeys());
    }

    @PostMapping("/enums")
    public Result<MetaEnumLibrary> saveEnum(@RequestBody MetaEnumLibrary enumLib) {
        enumService.saveOrUpdate(enumLib);
        return Result.ok(enumLib);
    }

    @DeleteMapping("/enums/{id}")
    public Result<String> deleteEnum(@PathVariable Long id) {
        enumService.removeById(id);
        return Result.ok("删除成功");
    }

    // ===================== 引用文件管理 =====================

    @GetMapping("/resources")
    public Result<List<MetaRefFile>> listRefFiles() {
        return Result.ok(refFileService.listAll());
    }

    @PostMapping("/resources/upload")
    public Result<MetaRefFile> uploadRefFile(@RequestParam("file") MultipartFile file,
                                              @RequestParam("refName") String refName) {
        try {
            File dir = new File(refFilePath);
            dir.mkdirs();
            String fileName = UUID.randomUUID().toString().substring(0, 8) + "_" + file.getOriginalFilename();
            File dest = new File(dir, fileName);
            file.transferTo(dest);
            MetaRefFile refFile = refFileService.uploadAndSave(refName, dest.getAbsolutePath(), "DELIMITER", ",", null);
            return Result.ok(refFile);
        } catch (IOException e) {
            log.error("上传失败", e);
            return Result.fail("上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/resources/define")
    public Result<String> defineRefFile(@RequestBody MetaRefFile refFile) {
        refFileService.saveOrUpdate(refFile);
        return Result.ok("保存成功");
    }

    @DeleteMapping("/resources/{id}")
    public Result<String> deleteRefFile(@PathVariable Long id) {
        boolean success = refFileService.deleteRefFile(id);
        if (success) {
            return Result.ok("删除成功");
        } else {
            return Result.fail("删除失败，文件不存在");
        }
    }

    @GetMapping("/resources/{id}/preview")
    public Result<List<String>> previewRefFile(@PathVariable Long id,
                                               @RequestParam(defaultValue = "5") int lineCount) {
        List<String> lines = refFileService.previewRefFile(id, lineCount);
        return Result.ok(lines);
    }

    // ===================== FTP配置管理 =====================

    @GetMapping("/ftp-configs")
    public Result<List<MetaFtpConfig>> listFtpConfigs() {
        return Result.ok(ftpConfigService.listAll());
    }

    @PostMapping("/ftp-configs")
    public Result<MetaFtpConfig> saveFtpConfig(@RequestBody MetaFtpConfig config) {
        String empNo = EmpContext.getEmpNo();
        if (config.getId() == null) {
            config.setCreateUser(empNo);
        }
        ftpConfigService.saveOrUpdate(config);
        return Result.ok(config);
    }

    @DeleteMapping("/ftp-configs/{id}")
    public Result<String> deleteFtpConfig(@PathVariable Long id) {
        ftpConfigService.removeById(id);
        return Result.ok("删除成功");
    }

    // ===================== 模板管理 =====================

    @GetMapping("/template/downloadByKeyword")
    public void downloadTemplateByKeyword(@RequestParam String keyword,
                                          HttpServletResponse response) {
        File directory = new File(templatePath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && file.getName().contains(keyword)) {
                        downloadFile(file, response);
                        return;
                    }
                }
            }
        }
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        try {
            response.getWriter().write("{\"code\":404,\"message\":\"未找到匹配的模板文件\"}");
        } catch (IOException ignored) {}
    }

    @GetMapping("/template/general/download")
    public void downloadGeneralTemplate(HttpServletResponse response) {
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + URLEncoder.encode("通用字段模板.xlsx", "UTF-8") + "\"");

            // 表头：与上传解析格式一致（rule_config_json 作为第6列）
            List<List<String>> head = new ArrayList<>();
            head.add(Collections.singletonList("字段英文名"));
            head.add(Collections.singletonList("字段描述"));
            head.add(Collections.singletonList("长度(字节)"));
            head.add(Collections.singletonList("是否必填"));
            head.add(Collections.singletonList("规则类型"));
            head.add(Collections.singletonList("规则配置JSON"));
            head.add(Collections.singletonList("说明"));

            // 数据行：14种规则类型，第6列直接写完整的 rule_config_json
            List<List<String>> data = new ArrayList<>();

            data.add(Arrays.asList("fixField", "固定值示例", "10", "是", "固定值",
                    "{\"value\": \"HELLO\"}",
                    "固定输出不变的字符串；value=固定文本内容，长度不得超过字段定义的length"));
            data.add(Arrays.asList("dateField", "日期示例", "8", "是", "日期",
                    "{\"format\": \"yyyyMMdd\", \"offset\": 0}",
                    "按指定格式生成当前日期；format支持yyyyMMdd/yyyyMMddHHmmss/HHmmss等；offset=天数偏移(0=今天,-1=昨天)"));
            data.add(Arrays.asList("enumField", "枚举示例", "2", "否", "枚举",
                    "{\"enumKey\": \"gender\", \"useDefault\": \"0\"}",
                    "从枚举库中随机取一个值；enumKey=枚举库已定义的Key；useDefault=枚举库为空时的默认值"));
            data.add(Arrays.asList("bodySeqNo", "序列号示例", "12", "是", "序列号",
                    "{\"prefix\": \"S\", \"start\": 1, \"max\": 99999999999, \"cycle\": true, \"step\": 1, \"updateStrategy\": \"PER_LINE\", \"digitLength\": 11}",
                    "带前缀S的12位定长序列号(前缀1位+数字11位)；prefix=前缀；digitLength=数字位数(左补零)；前缀长度+digitLength须等于length"));
            data.add(Arrays.asList("randCnField", "随机汉字示例", "9", "否", "随机汉字",
                    "{\"count\": 3}",
                    "随机生成指定个数的汉字；count=汉字数量(每个汉字占3字节UTF-8)"));
            data.add(Arrays.asList("randNumField", "随机数字示例", "10", "否", "随机数字",
                    "{\"count\": 5}",
                    "随机生成指定位数的数字字符串；count=数字位数"));
            data.add(Arrays.asList("randUuidField", "随机UUID示例", "32", "否", "随机UUID",
                    "{\"upperCase\": true}",
                    "随机生成32位UUID(不含横杠)；upperCase=true大写/false小写"));
            data.add(Arrays.asList("refFileField", "引用文件示例", "20", "否", "引用文件",
                    "{\"refFileId\": 10, \"columnKey\": \"userName\", \"updateStrategy\": \"PER_LINE\", \"atEnd\": \"LOOP\"}",
                    "从素材文件按行引用数据；refFileId=素材文件ID；columnKey=引用的列名；atEnd=LOOP循环/STOP耗尽停止"));
            data.add(Arrays.asList("refFieldField", "引用字段示例", "10", "否", "引用字段",
                    "{\"targetFieldKey\": \"fixField\"}",
                    "引用同报文前面已定义的字段值；targetFieldKey=目标字段英文名(只能引用当前字段前面的字段)"));
            data.add(Arrays.asList("headTotalSum", "汇总金额示例", "16", "否", "汇总金额",
                    "{\"targetFieldKey\": \"tran_amt\", \"format\": \"9(14)V99\"}",
                    "仅文件头/文件尾可用；引擎先处理Body累加后回写；format=9(N)V99表示N位整数+2位隐式小数"));
            data.add(Arrays.asList("headTotalCount", "统计行数示例", "8", "否", "统计行数",
                    "{\"targetFieldKey\": \"body_row\"}",
                    "仅文件头/文件尾可用；自动累计Body数据总行数；targetFieldKey=Body中任意字段名(用于定位Body段)"));
            data.add(Arrays.asList("batchNoField", "批次号示例", "10", "是", "批次号",
                    "{\"prefix\": \"B\", \"start\": 1, \"updateStrategy\": \"PER_FILE\"}",
                    "全局批次号，每生成一个文件递增一次(PER_FILE)；与序列号不同，序列号每行递增(PER_LINE)"));
            data.add(Arrays.asList("tranAmt", "金额示例", "16", "是", "金额",
                    "{\"value\": 123.45, \"format\": \"9(14)V99\"}",
                    "按金融报文标准格式生成金额；format=9(N)V99隐式小数/9(N).99显式小数；value仅为示例参考值"));
            data.add(Arrays.asList("exprField", "表达式示例", "35", "否", "表达式",
                    "{\"pattern\": \"PROJ_${dateField}_${batchNo}\", \"desc\": \"动态文件名\"}",
                    "使用${变量名}拼接已有字段；pattern=拼接模板；变量名须与前面字段的fieldKey一致"));

            EasyExcel.write(response.getOutputStream())
                    .head(head)
                    .sheet("通用字段模板")
                    .doWrite(data);
        } catch (IOException e) {
            log.error("生成通用模板失败", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try {
                response.getWriter().write("{\"code\":500,\"message\":\"生成通用模板失败\"}");
            } catch (IOException ignored) {}
        }
    }

    @PostMapping("/template/upload")
    public Result<String> uploadTemplate(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return Result.fail("文件不能为空");
        }
        try {
            File directory = new File(templatePath);
            if (!directory.exists()) {
                directory.mkdirs();
            }
            String originalFilename = file.getOriginalFilename();
            File dest = new File(templatePath + originalFilename);
            file.transferTo(dest);
            return Result.ok("上传成功");
        } catch (IOException e) {
            log.error("模板文件上传失败", e);
            return Result.fail("上传失败：" + e.getMessage());
        }
    }

    @GetMapping("/template/list")
    public Result<List<TemplateFileInfo>> listTemplates() {
        List<TemplateFileInfo> result = new ArrayList<>();
        File directory = new File(templatePath);
        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (f.isFile()) {
                        TemplateFileInfo info = new TemplateFileInfo();
                        info.setFileName(f.getName());
                        info.setFileSize(f.length());
                        info.setLastModified(new Date(f.lastModified()));
                        result.add(info);
                    }
                }
            }
        }
        result.sort((a, b) -> b.getLastModified().compareTo(a.getLastModified()));
        return Result.ok(result);
    }

    @GetMapping("/template/download")
    public void downloadTemplate(@RequestParam String fileName,
                                 HttpServletResponse response) {
        File file = new File(templatePath + fileName);
        if (!file.exists() || !file.isFile()) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            try {
                response.getWriter().write("{\"code\":404,\"message\":\"文件不存在\"}");
            } catch (IOException ignored) {}
            return;
        }
        downloadFile(file, response);
    }

    @DeleteMapping("/template/delete")
    public Result<String> deleteTemplate(@RequestParam String fileName) {
        File file = new File(templatePath + fileName);
        if (!file.exists() || !file.isFile()) {
            return Result.fail("文件不存在");
        }
        if (file.delete()) {
            return Result.ok("删除成功");
        } else {
            return Result.fail("删除失败");
        }
    }

    @PostMapping("/fields/parseExcel")
    public Result<List<Map<String, Object>>> parseFieldExcel(@RequestParam("file") MultipartFile file) {
        try {
            List<Map<String, Object>> result = new ArrayList<>();
            // 使用 EasyExcel 读取原始行（不指定 Model，返回 List<LinkedHashMap<Integer,String>>）
            File tempFile = File.createTempFile("field_template_", ".xlsx");
            file.transferTo(tempFile);
            @SuppressWarnings("unchecked")
            List<java.util.LinkedHashMap<Integer, String>> rawRows =
                (List<java.util.LinkedHashMap<Integer, String>>) (List<?>)
                EasyExcel.read(tempFile).sheet(0).headRowNumber(0).doReadSync();

            if (rawRows.size() > 1) {
                // 第一行（index=0）是表头，从第二行（index=1）开始读取数据
                for (int i = 1; i < rawRows.size(); i++) {
                    java.util.LinkedHashMap<Integer, String> row = rawRows.get(i);
                    if (row == null || row.isEmpty()) continue;

                    Map<String, Object> field = new LinkedHashMap<>();
                    field.put("sortIndex", (i - 1) * 10);
                    field.put("level", 1);
                    // 列0: 字段英文名
                    field.put("fieldKey", nvl(row.get(0), ""));
                    // 列1: 字段描述
                    field.put("fieldName", nvl(row.get(1), ""));
                    // 列2: 长度(字节)
                    Integer length = null;
                    String lenStr = row.get(2);
                    if (!isEmpty(lenStr)) {
                        try { length = Integer.parseInt(lenStr.trim()); } catch (NumberFormatException ignored) {}
                    }
                    field.put("length", length);
                    // 列3: 是否必填
                    Integer isRequired = 0;
                    String reqStr = row.get(3);
                    if (!isEmpty(reqStr)) {
                        String val = reqStr.trim();
                        if ("1".equals(val) || "是".equals(val) || "必填".equals(val) || "true".equalsIgnoreCase(val)) {
                            isRequired = 1;
                        }
                    }
                    field.put("isRequired", isRequired);
                    // 列4: 规则类型 - 支持中文映射
                    String rawRuleType = nvl(row.get(4), "").trim();
                    String ruleType = mapRuleType(rawRuleType);
                    field.put("ruleType", ruleType);
                    // 列5: 规则配置JSON（直接读取，兼容旧格式自动转换为标准JSON）
                    String rawConfigCol = nvl(row.get(5), "");
                    String ruleConfigJson = tryBuildRuleConfigJson(ruleType, rawConfigCol);
                    field.put("ruleConfigJson", ruleConfigJson);
                    // 列6: 说明
                    field.put("remark", nvl(row.get(6), ""));

                    result.add(field);
                }
            }
            tempFile.delete();
            return Result.ok(result);
        } catch (Exception e) {
            log.error("Excel 解析失败", e);
            return Result.fail("Excel 解析失败: " + e.getMessage());
        }
    }

    @PostMapping("/fields/exportExcel")
    public void exportFieldExcel(@RequestBody Map<String, Object> body,
                                 HttpServletResponse response) {
        try {
            String modelName = (String) body.getOrDefault("modelName", "未命名模型");
            String section = (String) body.getOrDefault("section", "BODY");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> fields = (List<Map<String, Object>>) body.get("fields");

            // 文件名：模型名称_section中文_字段定义_时间戳.xlsx
            String sectionLabel = section;
            switch (section) {
                case "FILENAME": sectionLabel = "文件名"; break;
                case "HEADER": sectionLabel = "文件头"; break;
                case "BODY": sectionLabel = "文件体"; break;
                case "FOOTER": sectionLabel = "文件尾"; break;
                default: break;
            }
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String fileName = modelName + "_" + sectionLabel + "_字段定义_" + timestamp + ".xlsx";

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + URLEncoder.encode(fileName, "UTF-8") + "\"");

            // 表头：第6列直接输出 rule_config_json 完整 JSON 串
            List<List<String>> head = new ArrayList<>();
            head.add(Collections.singletonList("字段英文名"));
            head.add(Collections.singletonList("字段描述"));
            head.add(Collections.singletonList("长度(字节)"));
            head.add(Collections.singletonList("是否必填"));
            head.add(Collections.singletonList("规则类型"));
            head.add(Collections.singletonList("规则配置JSON"));
            head.add(Collections.singletonList("说明"));

            List<List<String>> data = new ArrayList<>();
            if (fields != null) {
                for (Map<String, Object> f : fields) {
                    List<String> row = new ArrayList<>();
                    // 字段英文名
                    row.add(nvl((String) f.get("fieldKey"), ""));
                    // 字段描述
                    row.add(nvl((String) f.get("fieldName"), ""));
                    // 长度
                    Object lengthObj = f.get("length");
                    row.add(lengthObj != null ? String.valueOf(lengthObj) : "");
                    // 是否必填
                    Object isRequired = f.get("isRequired");
                    row.add(isRequired != null && (Integer.valueOf(String.valueOf(isRequired)) == 1) ? "是" : "否");
                    // 规则类型 → 中文（保持可读性）
                    String ruleType = nvl((String) f.get("ruleType"), "");
                    row.add(ruleTypeToChinese(ruleType));
                    // 规则配置JSON → 直接写入 ruleConfigJson 原始 JSON 串
                    String configJson = nvl((String) f.get("ruleConfigJson"), "");
                    row.add(configJson);
                    // 说明
                    row.add(nvl((String) f.get("remark"), ""));

                    data.add(row);
                }
            }

            EasyExcel.write(response.getOutputStream())
                    .head(head)
                    .sheet(sectionLabel + "字段定义")
                    .doWrite(data);
        } catch (IOException e) {
            log.error("导出字段Excel失败", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try {
                response.getWriter().write("{\"code\":500,\"message\":\"导出失败\"}");
            } catch (IOException ignored) {}
        }
    }

    private String ruleTypeToChinese(String ruleType) {
        if (ruleType == null) return "";
        switch (ruleType) {
            case "FIXED": return "固定值";
            case "DATE": return "日期";
            case "ENUM": return "枚举";
            case "SEQUENCE": return "序列号";
            case "RANDOM_CN": return "随机汉字";
            case "RANDOM_NUM": return "随机数字";
            case "RANDOM_UUID": return "随机UUID";
            case "REF_FILE": return "引用文件";
            case "REF_FIELD": return "引用字段";
            case "SUM": return "汇总金额";
            case "COUNT": return "统计行数";
            case "BATCH_NO": return "批次号";
            case "AMOUNT": return "金额";
            case "EXPR": return "表达式";
            default: return ruleType;
        }
    }

    // ===================== 工具方法 =====================

    private void downloadFile(File file, HttpServletResponse response) {
        try (FileInputStream fis = new FileInputStream(file);
             OutputStream os = response.getOutputStream()) {
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + URLEncoder.encode(file.getName(), "UTF-8") + "\"");
            byte[] buffer = new byte[4096];
            int len;
            while ((len = fis.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.flush();
        } catch (IOException e) {
            log.error("文件下载失败", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            try {
                response.getWriter().write("{\"code\":500,\"message\":\"文件下载失败\"}");
            } catch (IOException ignored) {}
        }
    }

    private String mapRuleType(String chineseType) {
        if (chineseType == null || chineseType.isEmpty()) return "FIXED";
        switch (chineseType) {
            case "固定值": return "FIXED";
            case "日期": return "DATE";
            case "枚举": return "ENUM";
            case "序列号": return "SEQUENCE";
            case "随机汉字": return "RANDOM_CN";
            case "随机数字": return "RANDOM_NUM";
            case "随机UUID": return "RANDOM_UUID";
            case "引用文件": return "REF_FILE";
            case "引用字段": return "REF_FIELD";
            case "汇总金额": return "SUM";
            case "统计行数": return "COUNT";
            case "批次号": return "BATCH_NO";
            case "金额": return "AMOUNT";
            case "表达式": return "EXPR";
            // 兼容旧版中文名称
            case "随机值": return "RANDOM_NUM";
            default: return chineseType.toUpperCase();
        }
    }

    /**
     * 尝试将 Excel 第6列解析为标准 rule_config_json 字符串。
     * 优先按新格式（合法 JSON 串）处理；若不是 JSON，则按旧格式（key=value 逗号分隔）兼容转换；
     * 若两者均无法匹配，则填入对应规则类型的默认模板 JSON。
     */
    private String tryBuildRuleConfigJson(String ruleType, String rawValue) {
        if (rawValue == null || rawValue.trim().isEmpty()) {
            return buildDefaultRuleConfigJson(ruleType);
        }
        String trimmed = rawValue.trim();
        // 先尝试作为合法 JSON 解析
        if (trimmed.startsWith("{")) {
            try {
                objectMapper.readTree(trimmed);
                return trimmed; // 合法 JSON，直接返回
            } catch (Exception ignored) { /* 解析失败，继续兼容转换 */ }
        }
        // 旧格式兼容转换
        ObjectNode obj = objectMapper.createObjectNode();
        try {
            switch (ruleType) {
                case "FIXED":
                    obj.put("value", trimmed);
                    break;
                case "DATE":
                    obj.put("format", trimmed);
                    obj.put("offset", 0);
                    break;
                case "ENUM":
                    obj.put("enumKey", trimmed);
                    obj.put("useDefault", "0");
                    break;
                case "SEQUENCE": {
                    // 旧格式：前缀=S,位数=11,起始=1,步长=1,策略=PER_LINE
                    String prefix = ""; int start = 1; int step = 1; int digitLen = 9; String strategy = "PER_LINE";
                    for (String part : trimmed.split(",")) {
                        String[] kv = part.trim().split("=", 2);
                        if (kv.length == 2) {
                            switch (kv[0].trim()) {
                                case "前缀": prefix = kv[1].trim(); break;
                                case "位数": digitLen = parseInt(kv[1].trim(), 9); break;
                                case "起始": start = parseInt(kv[1].trim(), 1); break;
                                case "步长": step = parseInt(kv[1].trim(), 1); break;
                                case "策略": strategy = kv[1].trim(); break;
                            }
                        }
                    }
                    obj.put("prefix", prefix); obj.put("start", start); obj.put("step", step);
                    obj.put("max", 9999999999L); obj.put("cycle", true);
                    obj.put("digitLength", digitLen); obj.put("updateStrategy", strategy);
                    break;
                }
                case "RANDOM_CN":
                case "RANDOM_NUM": {
                    int count = 3;
                    if (trimmed.contains("count=")) count = parseInt(trimmed.replaceAll(".*count=(\\d+).*", "$1"), 3);
                    else count = parseInt(trimmed, 3);
                    obj.put("count", count);
                    break;
                }
                case "RANDOM_UUID":
                    obj.put("upperCase", trimmed.toLowerCase().contains("true"));
                    break;
                case "REF_FILE": {
                    String refFileId = ""; String columnKey = "";
                    for (String part : trimmed.split(",")) {
                        String[] kv = part.trim().split("=", 2);
                        if (kv.length == 2) {
                            if ("refFileId".equals(kv[0].trim())) refFileId = kv[1].trim();
                            else if ("columnKey".equals(kv[0].trim())) columnKey = kv[1].trim();
                        }
                    }
                    obj.put("refFileId", refFileId.isEmpty() ? (JsonNode) null : objectMapper.getNodeFactory().numberNode(parseInt(refFileId, 0)));
                    obj.put("columnKey", columnKey); obj.put("updateStrategy", "PER_LINE"); obj.put("atEnd", "LOOP");
                    break;
                }
                case "REF_FIELD":
                    obj.put("targetFieldKey", trimmed);
                    break;
                case "SUM": {
                    String targetKey = ""; String format = "9(14)V99";
                    for (String part : trimmed.split(",")) {
                        String[] kv = part.trim().split("=", 2);
                        if (kv.length == 2) {
                            if ("targetFieldKey".equals(kv[0].trim())) targetKey = kv[1].trim();
                            else if ("format".equals(kv[0].trim())) format = kv[1].trim();
                        }
                    }
                    obj.put("targetFieldKey", targetKey); obj.put("format", format);
                    break;
                }
                case "COUNT": {
                    String targetKey2 = trimmed.contains("targetFieldKey=") ?
                            trimmed.replaceAll(".*targetFieldKey=([^,]+).*", "$1").trim() : trimmed;
                    obj.put("targetFieldKey", targetKey2);
                    break;
                }
                case "BATCH_NO": {
                    String prefix2 = "B"; int start2 = 1;
                    for (String part : trimmed.split(",")) {
                        String[] kv = part.trim().split("=", 2);
                        if (kv.length == 2) {
                            if ("前缀".equals(kv[0].trim())) prefix2 = kv[1].trim();
                            else if ("起始".equals(kv[0].trim())) start2 = parseInt(kv[1].trim(), 1);
                        }
                    }
                    obj.put("prefix", prefix2); obj.put("start", start2); obj.put("updateStrategy", "PER_FILE");
                    break;
                }
                case "AMOUNT":
                    obj.put("value", 123.45);
                    obj.put("format", trimmed.contains("(") ? trimmed : "9(14)V99");
                    break;
                case "EXPR":
                    obj.put("pattern", trimmed);
                    break;
                default:
                    return buildDefaultRuleConfigJson(ruleType);
            }
        } catch (Exception e) {
            return buildDefaultRuleConfigJson(ruleType);
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return buildDefaultRuleConfigJson(ruleType);
        }
    }

    private String buildDefaultRuleConfigJson(String ruleType) {
        if (ruleType == null) return "{}";
        switch (ruleType) {
            case "FIXED":    return "{\"value\": \"\"}";
            case "DATE":     return "{\"format\": \"yyyyMMdd\", \"offset\": 0}";
            case "ENUM":     return "{\"enumKey\": \"\", \"useDefault\": \"0\"}";
            case "SEQUENCE": return "{\"prefix\": \"\", \"start\": 1, \"max\": 9999999999, \"cycle\": true, \"step\": 1, \"updateStrategy\": \"PER_LINE\", \"digitLength\": 9}";
            case "RANDOM_CN":
            case "RANDOM_NUM": return "{\"count\": 3}";
            case "RANDOM_UUID": return "{\"upperCase\": true}";
            case "REF_FILE": return "{\"refFileId\": null, \"columnKey\": \"\", \"updateStrategy\": \"PER_LINE\", \"atEnd\": \"LOOP\"}";
            case "REF_FIELD": return "{\"targetFieldKey\": \"\"}";
            case "SUM":      return "{\"targetFieldKey\": \"\", \"format\": \"9(14)V99\"}";
            case "COUNT":    return "{\"targetFieldKey\": \"\"}";
            case "BATCH_NO": return "{\"prefix\": \"B\", \"start\": 1, \"updateStrategy\": \"PER_FILE\"}";
            case "AMOUNT":   return "{\"value\": 123.45, \"format\": \"9(14)V99\"}";
            case "EXPR":     return "{\"pattern\": \"\"}";
            default:         return "{}";
        }
    }

    private int parseInt(String s, int defaultVal) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return defaultVal; }
    }

    private String nvl(String val, String defaultVal) {
        return val != null ? val : defaultVal;
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    // ===================== DTO 内部类 =====================

    public static class ModelDetail {
        private MetaFileModel model;
        private List<MetaFieldDefinition> fields;

        public MetaFileModel getModel() { return model; }
        public void setModel(MetaFileModel model) { this.model = model; }
        public List<MetaFieldDefinition> getFields() { return fields; }
        public void setFields(List<MetaFieldDefinition> fields) { this.fields = fields; }
    }

    public static class ShareRequest {
        private String sharedWith;
        public String getSharedWith() { return sharedWith; }
        public void setSharedWith(String sharedWith) { this.sharedWith = sharedWith; }
    }

    public static class ValidateRequest {
        private Long modelId;
        private List<MetaFieldDefinition> fields;
        public Long getModelId() { return modelId; }
        public void setModelId(Long modelId) { this.modelId = modelId; }
        public List<MetaFieldDefinition> getFields() { return fields; }
        public void setFields(List<MetaFieldDefinition> fields) { this.fields = fields; }
    }

    public static class TemplateFileInfo {
        private String fileName;
        private long fileSize;
        private Date lastModified;

        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public long getFileSize() { return fileSize; }
        public void setFileSize(long fileSize) { this.fileSize = fileSize; }
        public Date getLastModified() { return lastModified; }
        public void setLastModified(Date lastModified) { this.lastModified = lastModified; }
    }
}

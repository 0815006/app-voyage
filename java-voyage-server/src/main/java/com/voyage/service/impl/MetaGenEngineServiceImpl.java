package com.voyage.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.voyage.entity.*;
import com.voyage.service.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@Slf4j
public class MetaGenEngineServiceImpl implements MetaGenEngineService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${meta.storage.preview:./storage/preview}")
    private String previewDir;

    @Value("${meta.storage.formal:./storage/formal}")
    private String formalDir;

    @Value("${meta.max-file-lines:200000}")
    private int maxFileLines;

    private final MetaFileModelService modelService;
    private final MetaFieldDefinitionService fieldService;
    private final MetaEnumLibraryService enumService;
    private final MetaRefFileService refFileService;
    private final MetaSequenceTrackerService seqService;
    private final MetaEntityFileService entityFileService;

    public MetaGenEngineServiceImpl(MetaFileModelService modelService,
                                     MetaFieldDefinitionService fieldService,
                                     MetaEnumLibraryService enumService,
                                     MetaRefFileService refFileService,
                                     MetaSequenceTrackerService seqService,
                                     MetaEntityFileService entityFileService) {
        this.modelService = modelService;
        this.fieldService = fieldService;
        this.enumService = enumService;
        this.refFileService = refFileService;
        this.seqService = seqService;
        this.entityFileService = entityFileService;
    }

    private static final DateTimeFormatter DF_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DF_DATETIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Override
    public String preview(Long modelId, String operator) {
        MetaFileModel model = modelService.getById(modelId);
        if (model == null) throw new IllegalArgumentException("模型不存在");

        List<MetaFieldDefinition> fields = fieldService.listByModelId(modelId);
        if (fields.isEmpty()) throw new IllegalArgumentException("模型无字段定义");

        // 预览时只生成3行Body
        GenerationContext ctx = new GenerationContext(model, fields, 3, true, operator);
        ctx.init();

        StringBuilder fullPreviewContent = new StringBuilder();

        // 1. 生成文件名
        String fileName = buildFileName(ctx, "PREVIEW", operator); // 预览文件使用特殊批次名
        fullPreviewContent.append("文件名: ").append(fileName).append(ctx.lineEnding).append(ctx.lineEnding);

        // 2. 先生成文件体 (3行) —— 需要先于 HEADER/FOOTER，以便金额汇总 (SUM) 字段能拿到正确的 bodySumAmount
        List<MetaFieldDefinition> bodyFields = ctx.getSectionFields("BODY");
        String bodyPreviewContent = "";
        if (!bodyFields.isEmpty()) {
            StringBuilder bodyBuilder = new StringBuilder();
            for (int row = 0; row < 3; row++) {
                StringBuilder line = new StringBuilder();
                Map<String, String> rowValues = new LinkedHashMap<>();
                for (MetaFieldDefinition f : bodyFields) {
                    String val = generateFieldValue(ctx, f, row);
                    rowValues.put(f.getFieldKey(), val);
                    if (f.getLevel() != null && f.getLevel() == 1) {
                        line.append(val);
                    }
                }
                String lineStr = substituteLevel2(bodyFields, rowValues, line.toString());
                bodyBuilder.append(lineStr);
                if (row < 2) bodyBuilder.append(ctx.lineEnding);
            }
            bodyPreviewContent = bodyBuilder.toString();
        }

        // 3. 生成文件头 (金额汇总已通过先跑 Body 完成累加)
        String headerPreviewContent = "";
        List<MetaFieldDefinition> headerFields = ctx.getSectionFields("HEADER");
        if (model.getHasHeader() != null && model.getHasHeader() == 1 && !headerFields.isEmpty()) {
            headerPreviewContent = "--- 文件头 (HEADER) ！！最终文件是没有这行的！！---" + ctx.lineEnding
                    + generateSectionContent(ctx, headerFields) + ctx.lineEnding + ctx.lineEnding;
        }

        // 4. 生成文件尾
        String footerPreviewContent = "";
        List<MetaFieldDefinition> footerFields = ctx.getSectionFields("FOOTER");
        if (model.getHasFooter() != null && model.getHasFooter() == 1 && !footerFields.isEmpty()) {
            footerPreviewContent = "--- 文件尾 (FOOTER) ！！最终文件是没有这行的！！---" + ctx.lineEnding
                    + generateSectionContent(ctx, footerFields) + ctx.lineEnding;
        }

        // 按文件顺序组装: HEADER → BODY → FOOTER
        fullPreviewContent.append(headerPreviewContent);
        if (!bodyPreviewContent.isEmpty()) {
            fullPreviewContent.append("--- 文件体 (BODY) ！！最终文件是没有这行的！！---").append(ctx.lineEnding);
            fullPreviewContent.append(bodyPreviewContent).append(ctx.lineEnding).append(ctx.lineEnding);
        }
        fullPreviewContent.append(footerPreviewContent);

        return fullPreviewContent.toString();
    }

    private String generateSectionContent(GenerationContext ctx, List<MetaFieldDefinition> fields) {
        StringBuilder line = new StringBuilder();
        Map<String, String> rowValues = new LinkedHashMap<>();
        for (MetaFieldDefinition f : fields) {
            String val = generateFieldValue(ctx, f, 0);
            rowValues.put(f.getFieldKey(), val);
            if (f.getLevel() != null && f.getLevel() == 1) {
                line.append(val);
            }
        }
        return substituteLevel2(fields, rowValues, line.toString());
    }

    @Override
    public MetaEntityFile generateAsync(Long modelId, Integer rowCount, String batchName, String operator) {
        MetaFileModel model = modelService.getById(modelId);
        if (model == null) throw new IllegalArgumentException("模型不存在");
        if (!"PUBLISHED".equals(model.getStatus())) throw new IllegalStateException("模型未发布，无法生成");
        if (rowCount == null || rowCount <= 0) rowCount = 1;
        if (rowCount > maxFileLines) throw new IllegalArgumentException("超过全局最大行数限制: " + maxFileLines);
        if (rowCount > model.getMaxRowsLimit()) throw new IllegalArgumentException("超过模型最大行数限制: " + model.getMaxRowsLimit());

        List<MetaFieldDefinition> fields = fieldService.listByModelId(modelId); // 获取 fields 列表
        // 使用 isPreview=true 避免文件名构造时意外持久化序列号
        GenerationContext tempCtx = new GenerationContext(model, fields, rowCount, true, operator);
        tempCtx.init(); // 初始化 ctx，生成 FILENAME 字段值

        String displayName = buildFileName(tempCtx, batchName, operator);
        // 注入随机数字串到磁盘文件名，防止同名文件覆盖
        String uniqueSuffix = String.format("%04x%08x",
                ThreadLocalRandom.current().nextInt(0x1000, 0xFFFF),
                ThreadLocalRandom.current().nextInt(0x10000000, 0x7FFFFFFF));
        String diskFileName = injectSuffix(displayName, "_" + uniqueSuffix);
        MetaEntityFile record = entityFileService.createRecord(modelId, displayName, "FORMAL", operator);
        // 异步执行，磁盘文件使用唯一文件名
        doGenerate(record.getId(), modelId, rowCount, diskFileName, operator);
        return record;
    }

    @Async
    public void doGenerate(Long recordId, Long modelId, Integer rowCount, String fileName, String operator) {
        long startMs = System.currentTimeMillis();
        MetaFileModel model = modelService.getById(modelId);
        List<MetaFieldDefinition> fields = fieldService.listByModelId(modelId);
        GenerationContext ctx = new GenerationContext(model, fields, rowCount, false, operator);
        // 不再调用 ctx.init()，避免 FILENAME 区块中的 SEQ 字段被意外持久化
        // 文件名已在 generateAsync 中提前构建，此处只需初始化虚拟变量
        ctx.fieldValues.put("BODY_SUM_AMOUNT", "0");
        ctx.fieldValues.put("BODY_COUNT", "0");

        String encoding = model.getEncoding() != null ? model.getEncoding() : "UTF-8";
        File tmpDir = new File(formalDir + "/tmp");
        tmpDir.mkdirs();

        File headerTmp = new File(tmpDir, recordId + "_header.tmp");
        File bodyTmp = new File(tmpDir, recordId + "_body.tmp");
        File footerTmp = new File(tmpDir, recordId + "_footer.tmp");
        File finalFile = new File(formalDir, fileName);

        try {
            // Step 3: 生成Body (流式写盘 + 统计)
            generateBodyToFile(ctx, bodyTmp, encoding);
            // Step 5: 生成Footer（此时汇总值已出）
            generateFooterToFile(ctx, footerTmp, encoding);
            // Step 6: 生成Header（回填汇总值）
            generateHeaderToFile(ctx, headerTmp, encoding);
            // Step 7: 合并文件
            mergeFiles(finalFile, encoding, headerTmp, bodyTmp, footerTmp);

            // 更新数据库
            MetaEntityFile record = entityFileService.getById(recordId);
            record.setStatus("SUCCESS");
            record.setStoragePath(finalFile.getAbsolutePath());
            record.setRowCount(rowCount);
            record.setDurationMs((int)(System.currentTimeMillis() - startMs));
            entityFileService.updateById(record);

            // 清理临时文件
            headerTmp.delete();
            bodyTmp.delete();
            footerTmp.delete();

            log.info("文件生成成功: {} ({}行, {}ms)", fileName, rowCount, record.getDurationMs());
        } catch (Exception e) {
            log.error("文件生成失败: {}", fileName, e);
            MetaEntityFile record = entityFileService.getById(recordId);
            record.setStatus("FAILED");
            record.setErrorMsg(e.getMessage());
            record.setDurationMs((int)(System.currentTimeMillis() - startMs));
            entityFileService.updateById(record);
            // 清理临时文件
            headerTmp.delete();
            bodyTmp.delete();
            footerTmp.delete();
        }
    }

    @Override
    public MetaEntityFile getStatus(Long taskId) {
        return entityFileService.getById(taskId);
    }

    @Override
    public String getFilePath(Long fileId) {
        MetaEntityFile record = entityFileService.getById(fileId);
        if (record == null) return null;
        if ("RUNNING".equals(record.getStatus())) throw new IllegalStateException("文件正在生成中，请稍后再试");
        return record.getStoragePath();
    }

    @Override
    public void deleteFile(Long fileId, String operator) {
        MetaEntityFile record = entityFileService.getById(fileId);
        if (record == null) return;
        // 删除物理文件
        if (record.getStoragePath() != null) {
            try { Files.deleteIfExists(Paths.get(record.getStoragePath())); } catch (IOException ignored) {}
        }
        if (record.getTempPath() != null) {
            try { Files.deleteIfExists(Paths.get(record.getTempPath())); } catch (IOException ignored) {}
        }
        entityFileService.removeById(fileId);
    }

    // ===================== 文件区块生成 =====================

    private void generateBodyToFile(GenerationContext ctx, File file, String encoding) throws IOException {
        List<MetaFieldDefinition> fields = ctx.getSectionFields("BODY");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding))) {
            for (int row = 0; row < ctx.totalRows; row++) {
                StringBuilder line = new StringBuilder();
                Map<String, String> rowValues = new LinkedHashMap<>();
                for (MetaFieldDefinition f : fields) {
                    String val = generateFieldValue(ctx, f, row);
                    rowValues.put(f.getFieldKey(), val);
                    if (f.getLevel() != null && f.getLevel() == 1) {
                        line.append(val);
                    }
                }
                // 处理 level 2 子字段替换
                String lineStr = substituteLevel2(fields, rowValues, line.toString());
                writer.write(lineStr);
                if (row < ctx.totalRows - 1) {
                    writer.write(ctx.lineEnding);
                }
                // 每1000行flush一次
                if ((row + 1) % 1000 == 0) {
                    writer.flush();
                }
            }
            writer.flush();
        }
    }

    private void generateFooterToFile(GenerationContext ctx, File file, String encoding) throws IOException {
        List<MetaFieldDefinition> fields = ctx.getSectionFields("FOOTER");
        if (fields.isEmpty()) {
            file.createNewFile();
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding))) {
            StringBuilder line = new StringBuilder();
            Map<String, String> rowValues = new LinkedHashMap<>();
            for (MetaFieldDefinition f : fields) {
                String val = generateFieldValue(ctx, f, 0);
                rowValues.put(f.getFieldKey(), val);
                if (f.getLevel() != null && f.getLevel() == 1) {
                    line.append(val);
                }
            }
            String lineStr = substituteLevel2(fields, rowValues, line.toString());
            writer.write(lineStr);
            writer.flush();
        }
    }

    private void generateHeaderToFile(GenerationContext ctx, File file, String encoding) throws IOException {
        List<MetaFieldDefinition> fields = ctx.getSectionFields("HEADER");
        if (fields.isEmpty()) {
            file.createNewFile();
            return;
        }
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), encoding))) {
            StringBuilder line = new StringBuilder();
            Map<String, String> rowValues = new LinkedHashMap<>();
            for (MetaFieldDefinition f : fields) {
                String val = generateFieldValue(ctx, f, 0);
                rowValues.put(f.getFieldKey(), val);
                if (f.getLevel() != null && f.getLevel() == 1) {
                    line.append(val);
                }
            }
            String lineStr = substituteLevel2(fields, rowValues, line.toString());
            writer.write(lineStr);
            writer.flush();
        }
    }

    private void mergeFiles(File output, String encoding, File header, File body, File footer) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), encoding))) {
            appendFile(writer, header, encoding);
            appendFile(writer, body, encoding);
            appendFile(writer, footer, encoding);
            writer.flush();
        }
    }

    private void appendFile(BufferedWriter writer, File file, String encoding) throws IOException {
        if (!file.exists() || file.length() == 0) return;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), encoding))) {
            String line;
            boolean first = true;
            while ((line = reader.readLine()) != null) {
                if (!first) writer.newLine();
                writer.write(line);
                first = false;
            }
        }
    }

    // ===================== 字段值生成 =====================

    private String generateFieldValue(GenerationContext ctx, MetaFieldDefinition field, int rowIndex) {
        String rawVal;
        String ruleType = field.getRuleType();
        ObjectNode ruleCfg = parseRuleConfig(field.getRuleConfigJson());

        switch (ruleType) {
            case "FIXED":
                rawVal = ruleCfg != null && ruleCfg.has("value") ? ruleCfg.get("value").asText() : "";
                break;
            case "DATE":
                rawVal = generateDate(ruleCfg);
                break;
            case "ENUM":
                rawVal = generateEnum(field.getRefEnumKey(), ruleCfg);
                break;
            case "REF_FILE":
                rawVal = generateRefFile(ctx, field, rowIndex);
                break;
            case "REF_FIELD":
                rawVal = ctx.getFieldValue(field.getRefFieldKey());
                break;
            case "SEQ":
            case "SEQUENCE": // 兼容前端 SEQUENCE 类型
            case "BATCH_NO": // 批次号，与序列号共用生成逻辑
                rawVal = generateSeq(ctx, field, rowIndex);
                break;
            case "SUM":
                rawVal = String.valueOf(ctx.bodySumAmount);
                break;
            case "COUNT":
                rawVal = String.valueOf(ctx.totalRows);
                break;
            case "RANDOM":
                rawVal = generateRandom(ruleCfg);
                break;
            case "RANDOM_NUM": {
                ObjectNode numCfg = ruleCfg != null ? ruleCfg : objectMapper.createObjectNode();
                if (!numCfg.has("mode")) numCfg.put("mode", "DIGIT");
                rawVal = generateRandom(numCfg);
                break;
            }
            case "RANDOM_CN": {
                ObjectNode cnCfg = ruleCfg != null ? ruleCfg : objectMapper.createObjectNode();
                if (!cnCfg.has("mode")) cnCfg.put("mode", "CHINESE");
                rawVal = generateRandom(cnCfg);
                break;
            }
            case "RANDOM_UUID": {
                ObjectNode uuidCfg = ruleCfg != null ? ruleCfg : objectMapper.createObjectNode();
                if (!uuidCfg.has("mode")) uuidCfg.put("mode", "UUID");
                rawVal = generateRandom(uuidCfg);
                break;
            }
            case "AMOUNT":
                rawVal = generateAmount(ctx, ruleCfg);
                break;
            case "EXPRESSION":
            case "EXPR": // 兼容前端 EXPR 类型
                rawVal = generateExpression(ctx, ruleCfg);
                break;
            default:
                log.warn("未知规则类型: {} (字段: {}), 将生成空值", ruleType, field.getFieldKey());
                rawVal = "";
        }

        // 存储生成的字段值，供后续 REF_FIELD 等引用
        ctx.setFieldValue(field.getFieldKey(), rawVal);

        // 定长/补齐处理
        return applyPadding(field, rawVal, ctx.encoding);
    }

    private String generateDate(ObjectNode cfg) {
        String fmt = cfg != null && cfg.has("format") ? cfg.get("format").asText() : "yyyyMMdd";
        int offset = cfg != null && cfg.has("offset") ? cfg.get("offset").asInt() : 0;

        LocalDateTime date = LocalDateTime.now().plusDays(offset);

        if (fmt.contains("D")) {
            int dayOfYear = date.getDayOfYear();
            String dayStr = String.format("%03d", dayOfYear);
            String placeholder = "DAY";
            String fmtWithPlaceholder = fmt.replaceAll("D+", placeholder);
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern(fmtWithPlaceholder);
            String result = date.format(dtf);
            return result.replace(placeholder, dayStr);
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(fmt);
        return date.format(dtf);
    }

    private String generateEnum(String enumKey, ObjectNode cfg) {
        // 优先从 ruleConfigJson 中读取 enumKey（兼容前端只存 ruleConfigJson 的情况）
        if (enumKey == null && cfg != null) {
            JsonNode enumKeyNode = cfg.get("enumKey");
            if (enumKeyNode != null) enumKey = enumKeyNode.asText();
        }
        if (enumKey == null) return "";
        MetaEnumLibrary enumLib = enumService.lambdaQuery().eq(MetaEnumLibrary::getEnumKey, enumKey).one();
        if (enumLib == null) return "";
        List<ObjectNode> items = parseObjectNodeArray(enumLib.getItems());
        if (items.isEmpty()) return "";

        // 如果配置中指定了 useDefault 且不为空，检查该值是否存在于枚举项中
        if (cfg != null) {
            JsonNode defaultValNode = cfg.get("useDefault");
            if (defaultValNode != null) {
                String defaultVal = defaultValNode.asText();
                if (defaultVal != null && !defaultVal.isEmpty()) {
                    for (ObjectNode item : items) {
                        JsonNode valNode = item.get("val");
                        if (valNode != null && defaultVal.equals(valNode.asText())) {
                            return defaultVal;
                        }
                    }
                }
            }
        }

        // 随机选一个
        int idx = new Random().nextInt(items.size());
        JsonNode valNode = items.get(idx).get("val");
        return valNode != null ? valNode.asText() : "";
    }

    private String generateRefFile(GenerationContext ctx, MetaFieldDefinition field, int rowIndex) {
        ObjectNode ruleCfg = parseRuleConfig(field.getRuleConfigJson());
        String columnKey = ruleCfg != null && ruleCfg.has("columnKey") ? ruleCfg.get("columnKey").asText() : null;
        // refFileId 优先从 ruleCfg 取，兼容从 field 属性取
        Long refFileId = null;
        if (ruleCfg != null && ruleCfg.has("refFileId") && !ruleCfg.get("refFileId").isNull()) {
            refFileId = ruleCfg.get("refFileId").asLong();
        } else {
            refFileId = field.getRefFileId();
        }
        if (refFileId == null) {
            log.warn("REF_FILE 字段 [{}] 未配置 refFileId，跳过", field.getFieldKey());
            return "";
        }
        if (columnKey == null || columnKey.isEmpty()) {
            log.warn("REF_FILE 字段 [{}] 未配置 columnKey，跳过", field.getFieldKey());
            return "";
        }

        // 缓存 key = refFileId + ":" + columnKey，区分同一文件不同列
        String cacheKey = refFileId + ":" + columnKey;
        List<String> columnData = ctx.refFileColumnCache.get(cacheKey);
        if (columnData == null) {
            MetaRefFile refFile = refFileService.getById(refFileId);
            if (refFile == null) {
                log.warn("REF_FILE 字段 [{}] 引用文件 id={} 不存在", field.getFieldKey(), refFileId);
                return "";
            }
            columnData = loadRefFileColumn(refFile, columnKey, ctx);
            ctx.refFileColumnCache.put(cacheKey, columnData);
        }
        if (columnData.isEmpty()) return "";
        int idx = rowIndex % columnData.size();
        return columnData.get(idx);
    }

    /**
     * 从引用文件加载指定列的所有行数据。
     * <ul>
     *   <li>DELIMITER 模式 column_mapping: {"fieldName": columnIndex, ...}（1-based）</li>
     *   <li>FIXED 模式 column_mapping: {"fieldName": {"start": startByte, "length": byteLen}, ...}（1-based byte offset）</li>
     * </ul>
     */
    private List<String> loadRefFileColumn(MetaRefFile refFile, String columnKey, GenerationContext ctx) {
        List<String> data = new ArrayList<>();
        ObjectNode mapping = null;
        if (refFile.getColumnMapping() != null) {
            try {
                JsonNode node = objectMapper.readTree(refFile.getColumnMapping());
                if (node.isObject()) mapping = (ObjectNode) node;
            } catch (Exception e) {
                log.warn("解析 columnMapping 失败: {}", refFile.getColumnMapping());
            }
        }

        boolean isDelimiter = "DELIMITER".equals(refFile.getParseType());

        if (isDelimiter) {
            // DELIMITER 模式：column_mapping = {"fieldName": columnIndex, ...}
            int colIdx = 1; // 默认第1列
            if (mapping != null && mapping.has(columnKey)) {
                JsonNode v = mapping.get(columnKey);
                if (v != null && v.isInt()) colIdx = v.asInt();
            }
            String delim = refFile.getDelimiter() != null ? refFile.getDelimiter() : ",";
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(refFile.getFilePath()), ctx.encoding))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    String[] cols = line.split(delim, -1);
                    if (colIdx <= cols.length) {
                        data.add(cols[colIdx - 1].trim());
                    }
                }
            } catch (IOException e) {
                log.error("加载引用文件失败 (DELIMITER): {}", refFile.getFilePath(), e);
            }
        } else {
            // FIXED 模式：column_mapping = {"fieldName": {"start": N, "length": M}, ...}（字节位置，1-based）
            int start = 0;
            int length = -1;
            if (mapping != null && mapping.has(columnKey)) {
                JsonNode posObj = mapping.get(columnKey);
                if (posObj != null && posObj.isObject()) {
                    JsonNode startNode = posObj.get("start");
                    if (startNode != null) start = startNode.asInt() - 1; // 转为0-based
                    JsonNode lengthNode = posObj.get("length");
                    if (lengthNode != null) length = lengthNode.asInt();
                }
            }
            Charset charset;
            try { charset = Charset.forName(ctx.encoding); } catch (Exception e) { charset = Charset.forName("UTF-8"); }
            final int finalStart = start;
            final int finalLength = length;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(refFile.getFilePath()), ctx.encoding))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    byte[] lineBytes = line.getBytes(charset);
                    if (finalStart >= lineBytes.length) {
                        data.add("");
                        continue;
                    }
                    int end = (finalLength > 0)
                            ? Math.min(finalStart + finalLength, lineBytes.length)
                            : lineBytes.length;
                    String val = new String(lineBytes, finalStart, end - finalStart, charset).trim();
                    data.add(val);
                }
            } catch (IOException e) {
                log.error("加载引用文件失败 (FIXED): {}", refFile.getFilePath(), e);
            }
        }
        return data;
    }

    private String generateSeq(GenerationContext ctx, MetaFieldDefinition field, int rowIndex) {
        String targetId = field.getModelId() + ":" + field.getFieldKey();
        long sequenceValue = seqService.nextValue("SEQ", targetId, ctx.isPreview);

        ObjectNode ruleCfg = parseRuleConfig(field.getRuleConfigJson());
        String prefix = "";
        int digitLength = 0;

        if (ruleCfg != null) {
            prefix = ruleCfg.has("prefix") ? ruleCfg.get("prefix").asText() : "";
            digitLength = ruleCfg.has("digitLength") ? ruleCfg.get("digitLength").asInt() : 0;
        }

        if (digitLength > 0) {
            return String.format("%s%0" + digitLength + "d", prefix, sequenceValue);
        } else {
            return prefix + sequenceValue;
        }
    }

    /** 常用汉字表（3500 个一级常用字），避免生成生僻字乱码 */
    private static final String COMMON_CHINESE =
        "的一是在不了有和人这中大为上个国我以要他时来用们生到作地于出就分对成会可主发年动同工也能下过子说产种面而方后多定行学法所民得经十三之进着等部度" +
        "家电力里如水化高自二理起小物现实加量都两体制机当使点从业本去把性好应开它合还因由其些然前外天政四日那社义事平形相全表间样与关各重新线内数正心" +
        "反你明看原又么利比或但质气第向道命此变条只没结解问意建月公无系军很情者最立代想已通并提直题党程展五果料象员革位入常文总次品式活设及管特件长" +
        "求老头基资边流路级少图山统接知较将组见计别她手角期根论运农指几九区强放决西被干做必战先回则任取据处队南给色光门即保治北造百规热领七海口东导" +
        "器压志世金增争济阶油思术极交受联什认六共权收证改清己美再采转更单风切打白教速花带安场身车例真务具万每目至达走积示议声报斗完类八离华名确才" +
        "科张信马节话米整空元况今集温传土许步群广石记需段研界拉林律叫且究观越织装影算低持音众书布复容儿须际商非验连断深难近矿千周委素技备半办青省" +
        "列习响约支般史感劳便团往酸历市克何除消构府称太准精值号率族维划选标写存候毛亲快效斯院查江型眼王按格养易置派层片始却专状育厂京识适属圆包火" +
        "住调满县局照参红细引听该铁价严首底液官德随病苏失尔死讲配女黄推显谈罪神艺呢席含企望密批营项防举球英氧势告李台落木帮轮破亚师围注远字材排供" +
        "河态封另施减树溶怎止案言士均武固叶鱼波视仅费紧爱左章早朝害续轻服试食充兵源判护司足某练差致板田降黑犯负击范继兴似余坚曲输修故城夫够送笔船" +
        "占右财吃富春职觉汉画功巴跟虽杂飞检吸助升阳互初创抗考投坏策古径换未跑留钢曾端责站简述钱副尽帝射草冲承独令限阿宣环双请超微让控州良轴找否纪" +
        "益依优顶础载倒房突坐粉敌略客袁冷胜绝块测丝协诉念陈仍罗盐友洋错苦夜刑移频逐靠混母短皮终聚汽村云哪既距卫停烈央察烧迅境若印洲刻括激孔搞甚室" +
        "待核校散侵吧甲游久菜味旧模湖货损预阻毫普稳乙妈植息扩银语挥酒守拿序纸医缺雨吗针刘啊急唱误训愿审附获茶鲜粮斤孩脱硫肥善龙演父渐血欢械掌歌沙" +
        "著刚攻谓盾讨晚粒乱燃矛乎杀药宁鲁贵钟煤读班伯香介迫句丰培握兰担弦蛋沉假穿执答乐谁顺烟缩征脸喜松脚困异免背星福买染井概慢怕磁倍祖皇促静补评" +
        "翻肉践尼衣宽扬棉希伤操垂秋宜氢套督振架亮末宪庆编牛触映雷销诗座居抓裂胞呼娘景威绿晶厚盟衡鸡孙延危胶屋乡临陆顾掉呀灯岁措束耐剧玉赵跳哥季" +
        "课凯胡额款绍卷齐伟蒸殖勇苗川炉岩弱零杨奏沿露杆滑镇饭浓航怀赶库夺伊灵税途灭赛归召鼓播盘裁险康唯录菌纯借糖盖横符私努堂域枪润幅哈竟熟虫泽脑" +
        "壤碳欧遍侧寨敢彻虑斜薄庭纳弹饲伸折麦湿暗荷瓦塞床筑恶户访塔奇透梁刀旋迹卡氯遇份毒泥退洗摆灰彩卖耗夏择忙铜献硬予繁圈雪函亦抽篇阵阴丁尺追堆" +
        "雄迎泛爸楼避谋吨野猪旗累偏典馆索秦脂潮爷豆忽托惊塑遗愈朱替纤粗倾尚痛楚谢奋购磨君池旁碎骨监捕弟暴割贯殊释词亡壁顿宝午尘闻揭炮残冬桥妇警综" +
        "招吴付浮遭徐您摇谷赞箱隔订男吹园纷唐败宋玻巨耕坦荣湾沿拿供坐怨逼销豫爆丽殊漂亮浮摸拒晨宽扶梯焦冠折胸碰殖抄拥徒掌宁祖陷钻猛射脑毕顶湾悠" +
        "瑞络诸迷纷逼移频姻俗脉宜欧彼暴忽鲜丙唯湿寿宜幽愤怒幻惠悲徐怒抵撑摸折扎撤摸撞择毅浑皆扩叙弃堆搜闲染洁奉闭奈悠";

    private String generateRandom(ObjectNode cfg) {
        String mode = cfg != null && cfg.has("mode") ? cfg.get("mode").asText() : "DIGIT";
        int len;
        if (cfg != null && cfg.has("count")) {
            len = cfg.get("count").asInt();
        } else if (cfg != null && cfg.has("length")) {
            len = cfg.get("length").asInt();
        } else {
            len = 10;
        }
        Random rnd = new Random();
        if ("CHINESE".equals(mode)) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < len; i++) {
                sb.append(COMMON_CHINESE.charAt(rnd.nextInt(COMMON_CHINESE.length())));
            }
            return sb.toString();
        }
        if ("UUID".equals(mode)) {
            return UUID.randomUUID().toString().replace("-", "");
        }
        // DIGIT
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(rnd.nextInt(10));
        }
        return sb.toString();
    }

    private String generateAmount(GenerationContext ctx, ObjectNode cfg) {
        // 兼容两种格式:
        // 1. 扁平: {"value": 0.01, "format": "9(14)V99", "scale": 16, "precision": 2}
        // 2. 嵌套: {"type": "AMOUNT", "config": {"precision": 2, "scale": 14, "decimalMode": "IMPLICIT"}}
        ObjectNode config = cfg;
        if (config == null) config = objectMapper.createObjectNode();

        // 兼容嵌套格式：如果 cfg 里有 "config" 子对象，解包
        if (cfg != null && cfg.has("config")) {
            JsonNode configNode = cfg.get("config");
            if (configNode != null && configNode.isObject()) {
                config = (ObjectNode) configNode;
            }
        }

        int precision = config.has("precision") ? config.get("precision").asInt() : 2;
        int totalLength = config.has("scale") ? config.get("scale").asInt() : 16;
        String decimalMode = config.has("decimalMode") ? config.get("decimalMode").asText() : "IMPLICIT";

        long intPart;
        long fracPart;
        long totalInSmallestUnit;

        // 检查是否有指定金额值
        if (config.has("value")) {
            double amount = config.get("value").asDouble();
            totalInSmallestUnit = Math.round(amount * Math.pow(10, precision));
            intPart = totalInSmallestUnit / (long) Math.pow(10, precision);
            fracPart = totalInSmallestUnit % (long) Math.pow(10, precision);
        } else {
            // 生成随机金额
            Random rnd = new Random();
            long maxIntPart = (long) Math.pow(10, totalLength - precision) - 1;
            intPart = (long)(rnd.nextDouble() * maxIntPart);
            fracPart = rnd.nextInt((int) Math.pow(10, precision));
            totalInSmallestUnit = intPart * (long) Math.pow(10, precision) + fracPart;
        }

        // 累加金额到上下文，供 HEADER/FOOTER 的 SUM 字段汇总使用
        ctx.bodySumAmount += totalInSmallestUnit;
        ctx.lastGeneratedAmount = totalInSmallestUnit;

        if ("IMPLICIT".equals(decimalMode)) {
            String combined = String.format("%d%0" + precision + "d", intPart, fracPart);
            int currentLength = combined.getBytes(Charset.forName(ctx.encoding)).length;
            if (currentLength < totalLength) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < totalLength - currentLength; i++) {
                    sb.append('0');
                }
                sb.append(combined);
                return sb.toString();
            } else {
                return combined;
            }
        } else {
            int intPartLength = totalLength - precision - 1;
            String intStr = String.format("%0" + intPartLength + "d", intPart);
            String fracStr = String.format("%0" + precision + "d", fracPart);
            return intStr + "." + fracStr;
        }
    }

    private String generateExpression(GenerationContext ctx, ObjectNode cfg) {
        String func = cfg != null && cfg.has("func") ? cfg.get("func").asText() : "CONCAT";
        ArrayNode params = null;
        if (cfg != null && cfg.has("params")) {
            JsonNode paramsNode = cfg.get("params");
            if (paramsNode != null && paramsNode.isArray()) {
                params = (ArrayNode) paramsNode;
            }
        }
        if (params == null) return "";

        if ("CONCAT".equals(func)) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < params.size(); i++) {
                String p = params.get(i).asText();
                if (p.startsWith("${") && p.endsWith("}")) {
                    String key = p.substring(2, p.length() - 1);
                    String val = ctx.getFieldValue(key);
                    sb.append(val != null ? val : "");
                } else {
                    sb.append(p);
                }
            }
            return sb.toString();
        }
        return "";
    }

    // ===================== 补齐/截断处理 =====================

    private String applyPadding(MetaFieldDefinition field, String rawVal, String encoding) {
        if (rawVal == null) rawVal = "";
        int targetLen = field.getLength() != null ? field.getLength() : -1;
        String paddingDir = field.getPaddingDirection() != null ? field.getPaddingDirection() : "NONE";
        String paddingChar = field.getPaddingChar() != null ? field.getPaddingChar() : " ";

        if (targetLen <= 0 || "NONE".equals(paddingDir)) {
            return rawVal;
        }

        // 截断：按显示宽度（中文=2，ASCII=1）截断，确保不在字符中间切断
        if (getDisplayWidth(rawVal) > targetLen) {
            rawVal = truncateByDisplayWidth(rawVal, targetLen);
        }

        int displayWidth = getDisplayWidth(rawVal);
        if (displayWidth >= targetLen) {
            return rawVal;
        }

        // 补齐：按补齐字符的显示宽度逐个填充
        int padNeeded = targetLen - displayWidth;
        int padCharWidth = getCharDisplayWidth(paddingChar.codePointAt(0));
        int padCount = padNeeded / padCharWidth;

        StringBuilder sb = new StringBuilder();
        if ("LEFT".equals(paddingDir)) {
            for (int i = 0; i < padCount; i++) {
                sb.append(paddingChar);
            }
            sb.append(rawVal);
        } else {
            sb.append(rawVal);
            for (int i = 0; i < padCount; i++) {
                sb.append(paddingChar);
            }
        }
        return sb.toString();
    }

    /** 计算字符串的显示宽度：中文字符（含全角）计 2，ASCII 计 1 */
    private int getDisplayWidth(String str) {
        int width = 0;
        for (int i = 0; i < str.length(); i++) {
            int cp = str.codePointAt(i);
            width += getCharDisplayWidth(cp);
            if (Character.isSupplementaryCodePoint(cp)) {
                i++;
            }
        }
        return width;
    }

    /** 按显示宽度截断字符串，确保不切断多字节字符 */
    private String truncateByDisplayWidth(String str, int maxWidth) {
        StringBuilder sb = new StringBuilder();
        int currentWidth = 0;
        for (int i = 0; i < str.length(); i++) {
            int cp = str.codePointAt(i);
            int cw = getCharDisplayWidth(cp);
            if (currentWidth + cw > maxWidth) {
                break;
            }
            sb.appendCodePoint(cp);
            currentWidth += cw;
            if (Character.isSupplementaryCodePoint(cp)) {
                i++;
            }
        }
        return sb.toString();
    }

    /** 单个字符的显示宽度：全角/中文 = 2，其余 = 1 */
    private int getCharDisplayWidth(int codePoint) {
        return isFullWidth(codePoint) ? 2 : 1;
    }

    /** 判断是否全角字符（中文、日韩文、全角标点等） */
    private boolean isFullWidth(int cp) {
        // CJK 统一表意文字
        if (cp >= 0x4E00 && cp <= 0x9FFF) return true;
        // CJK 扩展 A
        if (cp >= 0x3400 && cp <= 0x4DBF) return true;
        // CJK 兼容表意文字
        if (cp >= 0xF900 && cp <= 0xFAFF) return true;
        // CJK 扩展 B-F (补充平面)
        if (cp >= 0x20000 && cp <= 0x2FFFF) return true;
        // 全角 ASCII/标点
        if (cp >= 0xFF01 && cp <= 0xFF60) return true;
        if (cp >= 0xFFE0 && cp <= 0xFFE6) return true;
        // CJK 符号和标点
        if (cp >= 0x3000 && cp <= 0x303F) return true;
        // 日文平假名/片假名
        if (cp >= 0x3040 && cp <= 0x30FF) return true;
        // 韩文
        if (cp >= 0xAC00 && cp <= 0xD7AF) return true;
        if (cp >= 0x1100 && cp <= 0x11FF) return true;
        return false;
    }

    private String substituteLevel2(List<MetaFieldDefinition> fields, Map<String, String> rowValues, String parentLine) {
        return parentLine; // TODO: 完整实现需按偏移量替换
    }

    private ObjectNode parseRuleConfig(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isObject()) return (ObjectNode) node;
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 解析 JSON 数组字符串为 ObjectNode 列表
     */
    private List<ObjectNode> parseObjectNodeArray(String json) {
        List<ObjectNode> result = new ArrayList<>();
        if (json == null || json.trim().isEmpty()) return result;
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node.isArray()) {
                for (JsonNode item : node) {
                    if (item.isObject()) result.add((ObjectNode) item);
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return result;
    }

    private String buildFileName(GenerationContext ctx, String batchName, String operator) {
        // 从 ctx 中获取 FILENAME 区块的字段值来构建文件名
        List<MetaFieldDefinition> fileNameFields = ctx.getSectionFields("FILENAME");
        if (!fileNameFields.isEmpty()) {
            StringBuilder nameBuilder = new StringBuilder();
            for (MetaFieldDefinition f : fileNameFields) {
                // generateFieldValue 已经在 GenerationContext.init() 中调用并存储了结果
                String val = ctx.getFieldValue(f.getFieldKey());
                if (val != null) {
                    nameBuilder.append(val);
                }
            }
            // 检查生成的文件名是否已经包含扩展名
            String generatedName = nameBuilder.toString();
            if (!generatedName.contains(".")) { // 如果没有显式扩展名，则自动添加 .txt
                generatedName += ".txt";
            }
            return generatedName;
        } else {
            // 如果没有配置 FILENAME 区块，则回退到原来的逻辑
            String ts = LocalDateTime.now().format(DF_DATETIME);
            String name = ctx.model.getModelName() + "_" + ts;
            if (batchName != null && !batchName.isEmpty()) name += "_" + batchName;
            return name + ".txt"; // 默认加 .txt 后缀
        }
    }

    /** 在文件名扩展名前注入后缀，如 MyFile.txt → MyFile_a1b2c3d4.txt */
    private String injectSuffix(String fileName, String suffix) {
        int dotIdx = fileName.lastIndexOf('.');
        if (dotIdx > 0) {
            return fileName.substring(0, dotIdx) + suffix + fileName.substring(dotIdx);
        }
        return fileName + suffix;
    }

    // ===================== 内部类: 生成上下文 =====================

    class GenerationContext {
        MetaFileModel model;
        List<MetaFieldDefinition> fields;
        int totalRows;
        boolean isPreview;
        String operator;
        String encoding;
        String lineEnding;

        // 运行时值存储
        Map<String, String> fieldValues = new LinkedHashMap<>();
        Map<Long, List<String>> refFileCache;
        Map<String, List<String>> refFileColumnCache = new HashMap<>();
        long bodySumAmount = 0;
        long bodyCount = 0;
        long lastGeneratedAmount = 0;

        GenerationContext(MetaFileModel model, List<MetaFieldDefinition> fields, int totalRows, boolean isPreview, String operator) {
            this.model = model;
            this.fields = fields;
            this.totalRows = totalRows;
            this.isPreview = isPreview;
            this.operator = operator;
            this.encoding = model.getEncoding() != null ? model.getEncoding() : "UTF-8";
            this.lineEnding = model.getLineEndingChar() != null && !model.getLineEndingChar().isEmpty() ? model.getLineEndingChar() : "\r\n";
        }

        void init() {
            // 注册虚拟变量
            fieldValues.put("BODY_SUM_AMOUNT", "0");
            fieldValues.put("BODY_COUNT", "0");
            // 预生成FileName区块的值
            for (MetaFieldDefinition f : getSectionFields("FILENAME")) {
                fieldValues.put(f.getFieldKey(), generateFieldValue(this, f, 0));
            }
        }

        List<MetaFieldDefinition> getSectionFields(String section) {
            List<MetaFieldDefinition> list = new ArrayList<>();
            for (MetaFieldDefinition f : fields) {
                if (section.equals(f.getSection())) {
                    list.add(f);
                }
            }
            return list;
        }

        String getFieldValue(String key) {
            return fieldValues.get(key);
        }

        void setFieldValue(String key, String value) {
            fieldValues.put(key, value);
        }
    }
}

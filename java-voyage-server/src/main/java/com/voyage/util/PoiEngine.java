package com.voyage.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.xmlbeans.impl.values.XmlValueDisconnectedException;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 通用 Word (docx) 模板填充引擎
 */
@Slf4j
@SuppressWarnings("unused")
public final class PoiEngine {

    private PoiEngine() {
    }

    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\$\\{([^}]+)\\}|\\$\\[([^\\]]+)\\]|\\{\\{([^}]+)\\}\\}");

    private static final String NULL_VALUE_DEFAULT = "未获取到数据";
    private static final String NULL_VALUE_COLOR = "0000FF";
    private static final String MANUAL_FILL_COLOR = "C00000";

    // ==================== 公共入口 ====================

    public static void fillModel(InputStream templateInputStream,
                                  OutputStream outputStream,
                                  Map<String, Object> dataModel) {
        fillModel(templateInputStream, outputStream, dataModel, null);
    }

    public static void fillModel(InputStream templateInputStream,
                                  OutputStream outputStream,
                                  Map<String, Object> dataModel,
                                  Map<String, ImageInfo> imageData) {
        Map<String, String> singleData = new LinkedHashMap<>();
        Map<String, List<Map<String, String>>> listData = new LinkedHashMap<>();
        splitDataModel(dataModel, singleData, listData);
        fillInternal(templateInputStream, outputStream, singleData, listData, imageData);
    }

    public static void fill(InputStream templateInputStream,
                            OutputStream outputStream,
                            Map<String, String> singleData,
                            Map<String, List<Map<String, String>>> listData) {
        fill(templateInputStream, outputStream, singleData, listData, null);
    }

    public static void fill(InputStream templateInputStream,
                            OutputStream outputStream,
                            Map<String, String> singleData,
                            Map<String, List<Map<String, String>>> listData,
                            Map<String, ImageInfo> imageData) {
        fillInternal(templateInputStream, outputStream, singleData, listData, imageData);
    }

    // ==================== 内部核心处理 ====================

    private static void fillInternal(InputStream templateInputStream,
                                     OutputStream outputStream,
                                     Map<String, String> singleData,
                                     Map<String, List<Map<String, String>>> listData,
                                     Map<String, ImageInfo> imageData) {
        XWPFDocument document = null;
        try {
            long startTime = System.currentTimeMillis();
            document = new XWPFDocument(templateInputStream);

            log.info("Resolve the document start...");
            Set<String> allPlaceholders = collectAllPlaceholders(document);
            log.info("Resolve the document end, resolve and create {} MetaTemplates.", allPlaceholders.size());

            Set<String> modelKeys = new LinkedHashSet<>();
            if (singleData != null) modelKeys.addAll(singleData.keySet());
            if (listData != null) modelKeys.addAll(listData.keySet());
            log.info("Render template start... DataModel 共 {} 个字段", modelKeys.size());

            if (singleData != null && !singleData.isEmpty()) {
                handleImageBase64FromSingleData(document, singleData);
            }

            if (imageData != null && !imageData.isEmpty()) {
                replaceImagePlaceholders(document, imageData);
            }

            Set<String> listNames = listData != null ? listData.keySet() : Collections.<String>emptySet();
            for (XWPFTable table : document.getTables()) {
                String detectedListName = detectListName(table, listNames);
                if (detectedListName != null && listData != null) {
                    handleDynamicTable(table, detectedListName, listData.get(detectedListName));
                } else {
                    handleStaticTable(table, singleData);
                }
            }

            if (singleData != null && !singleData.isEmpty()) {
                replaceParagraphPlaceholders(document, singleData);
            }

            document.write(outputStream);
            outputStream.flush();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Successfully Render template in {} millis", elapsed);

        } catch (Exception e) {
            log.error("Word 模板填充失败", e);
            throw new RuntimeException("Word 模板填充失败: " + e.getMessage(), e);
        } finally {
            closeQuietly(document);
        }
    }

    @SuppressWarnings("unchecked")
    private static void splitDataModel(Map<String, Object> dataModel,
                                       Map<String, String> singleData,
                                       Map<String, List<Map<String, String>>> listData) {
        if (dataModel == null) return;
        for (Map.Entry<String, Object> entry : dataModel.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                singleData.put(key, "");
            } else if (value instanceof List) {
                List<?> rawList = (List<?>) value;
                List<Map<String, String>> rows = new ArrayList<>();
                for (Object item : rawList) {
                    if (item instanceof Map) {
                        Map<String, Object> rowMap = (Map<String, Object>) item;
                        Map<String, String> stringRow = new LinkedHashMap<>();
                        for (Map.Entry<String, Object> rowEntry : rowMap.entrySet()) {
                            Object v = rowEntry.getValue();
                            stringRow.put(rowEntry.getKey(), v != null ? v.toString() : "");
                        }
                        rows.add(stringRow);
                    }
                }
                if (!rows.isEmpty()) {
                    listData.put(key, rows);
                }
            } else {
                singleData.put(key, value.toString());
            }
        }
    }

    private static void replaceParagraphPlaceholders(XWPFDocument document, Map<String, String> dataMap) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            List<XWPFRun> runs = paragraph.getRuns();
            if (runs == null || runs.isEmpty()) continue;

            String paragraphText = getParagraphFullText(paragraph);
            if (paragraphText.isEmpty()) continue;

            Matcher matcher = PLACEHOLDER_PATTERN.matcher(paragraphText);
            StringBuffer sb = new StringBuffer();
            boolean hasMissing = false;

            while (matcher.find()) {
                String rawKey = extractPlaceholderKey(matcher);
                if (rawKey == null) continue;

                String value;
                if (rawKey.contains(".")) {
                    value = "[" + rawKey + "]";
                } else {
                    value = dataMap.get(rawKey);
                }

                if (value != null) {
                    if (rawKey.startsWith("delete")) {
                        matcher.appendReplacement(sb, "");
                    } else if (rawKey.startsWith("IMAGE_PLACEHOLDER")) {
                        matcher.appendReplacement(sb, "");
                    } else if (rawKey.startsWith("IMAGE_DESC")) {
                        String descValue = dataMap.get(rawKey);
                        matcher.appendReplacement(sb,
                                Matcher.quoteReplacement(descValue != null ? descValue : ""));
                    } else {
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
                    }
                } else {
                    if (rawKey.startsWith("IMAGE_") || rawKey.startsWith("delete")) {
                        matcher.appendReplacement(sb, "");
                    } else {
                        matcher.appendReplacement(sb, Matcher.quoteReplacement(NULL_VALUE_DEFAULT));
                        hasMissing = true;
                    }
                }
            }
            matcher.appendTail(sb);

            String replacedText = sb.toString();
            if (replacedText.equals(paragraphText)) continue;

            for (int i = runs.size() - 1; i >= 0; i--) {
                paragraph.removeRun(i);
            }
            if (!replacedText.isEmpty()) {
                XWPFRun newRun = paragraph.createRun();
                setTextWithNewLines(newRun, replacedText);
                if (hasMissing) newRun.setColor(NULL_VALUE_COLOR);
            }
        }
    }

    private static String detectListName(XWPFTable table, Set<String> candidateListNames) {
        if (table.getRows().size() < 2) return null;
        XWPFTableRow templateRow = table.getRow(1);
        if (templateRow == null) return null;

        String rowText = getRowFullText(templateRow);
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(rowText);
        while (matcher.find()) {
            String rawKey = extractPlaceholderKey(matcher);
            if (rawKey == null) continue;
            int dotIdx = rawKey.indexOf('.');
            if (dotIdx > 0) {
                String listName = rawKey.substring(0, dotIdx);
                if (candidateListNames.isEmpty() || candidateListNames.contains(listName)) {
                    return listName;
                }
            }
        }
        return null;
    }

    private static void handleDynamicTable(XWPFTable table, String listName,
                                            List<Map<String, String>> rows) {
        XWPFTableRow templateRow = table.getRow(1);
        if (templateRow == null) return;

        int rowCount = (rows != null) ? rows.size() : 0;
        int cellCount = templateRow.getTableCells().size();

        if (rowCount > 0) {
            for (int i = 0; i < rowCount; i++) {
                XWPFTableRow newRow = table.createRow();
                ensureRowCellCount(newRow, cellCount);
                copyRowAndFill(templateRow, newRow, listName, rows.get(i));
            }
            table.removeRow(1);
        } else {
            fillEmptyTemplateRow(templateRow, listName);
        }
    }

    private static void copyRowAndFill(XWPFTableRow templateRow, XWPFTableRow newRow,
                                        String listName, Map<String, String> rowData) {
        List<XWPFTableCell> templateCells = templateRow.getTableCells();

        for (int j = 0; j < templateCells.size(); j++) {
            XWPFTableCell templateCell = templateCells.get(j);
            XWPFTableCell newCell = newRow.getCell(j);

            clearCellContent(newCell);

            for (XWPFParagraph templatePara : templateCell.getParagraphs()) {
                XWPFParagraph newPara = newCell.addParagraph();
                newPara.setAlignment(templatePara.getAlignment());

                String cellText = getParagraphFullText(templatePara);
                if (cellText.isEmpty()) continue;

                String replacedText = replaceListPlaceholders(cellText, listName, rowData);

                XWPFRun newRun = newPara.createRun();
                List<XWPFRun> templateRuns = templatePara.getRuns();
                if (templateRuns != null && !templateRuns.isEmpty()) {
                    copyRunStyle(templateRuns.get(0), newRun);
                }
                newRun.setText(replacedText, 0);
            }
        }
    }

    private static String replaceListPlaceholders(String text, String listName, Map<String, String> rowData) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String rawKey = extractPlaceholderKey(matcher);
            if (rawKey == null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
                continue;
            }

            int dotIdx = rawKey.indexOf('.');
            if (dotIdx > 0) {
                String ln = rawKey.substring(0, dotIdx);
                if (ln.equals(listName)) {
                    String fieldName = rawKey.substring(dotIdx + 1);
                    String value = rowData.get(fieldName);
                    matcher.appendReplacement(sb,
                            Matcher.quoteReplacement(value != null ? value : NULL_VALUE_DEFAULT));
                    continue;
                }
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group(0)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static void handleStaticTable(XWPFTable table, Map<String, String> dataMap) {
        if (dataMap == null || dataMap.isEmpty()) return;

        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    String paragraphText = getParagraphFullText(paragraph);
                    if (paragraphText.isEmpty()) continue;

                    Matcher m = PLACEHOLDER_PATTERN.matcher(paragraphText);
                    StringBuffer sb = new StringBuffer();
                    while (m.find()) {
                        String rawKey = extractPlaceholderKey(m);
                        if (rawKey == null) {
                            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                            continue;
                        }
                        if (rawKey.contains(".")) {
                            m.appendReplacement(sb, Matcher.quoteReplacement(m.group(0)));
                        } else {
                            String val = dataMap.get(rawKey);
                            m.appendReplacement(sb, Matcher.quoteReplacement(
                                    val != null ? val : NULL_VALUE_DEFAULT));
                        }
                    }
                    m.appendTail(sb);

                    String replaced = sb.toString();
                    if (replaced.equals(paragraphText)) continue;

                    List<XWPFRun> runs = paragraph.getRuns();
                    for (int i = runs.size() - 1; i >= 0; i--) {
                        paragraph.removeRun(i);
                    }
                    if (!replaced.isEmpty()) {
                        XWPFRun newRun = paragraph.createRun();
                        newRun.setText(replaced, 0);
                    }
                }
            }
        }
    }

    private static void fillEmptyTemplateRow(XWPFTableRow row, String listName) {
        for (XWPFTableCell cell : row.getTableCells()) {
            for (XWPFParagraph paragraph : cell.getParagraphs()) {
                String text = getParagraphFullText(paragraph);
                if (!containsListPlaceholder(text, listName)) continue;

                List<XWPFRun> runs = paragraph.getRuns();
                for (int i = runs.size() - 1; i >= 0; i--) {
                    paragraph.removeRun(i);
                }
                XWPFRun newRun = paragraph.createRun();
                newRun.setText(NULL_VALUE_DEFAULT, 0);
                newRun.setColor(NULL_VALUE_COLOR);
            }
        }
    }

    private static boolean containsListPlaceholder(String text, String listName) {
        Matcher m = PLACEHOLDER_PATTERN.matcher(text);
        while (m.find()) {
            String rawKey = extractPlaceholderKey(m);
            if (rawKey != null) {
                int dotIdx = rawKey.indexOf('.');
                if (dotIdx > 0 && rawKey.substring(0, dotIdx).equals(listName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void handleImageBase64FromSingleData(XWPFDocument document, Map<String, String> singleData) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            List<XWPFRun> runs = paragraph.getRuns();
            if (runs == null) continue;

            for (int i = 0; i < runs.size(); i++) {
                XWPFRun run = runs.get(i);
                String text = run.getText(0);
                if (text == null) continue;

                for (Map.Entry<String, String> entry : singleData.entrySet()) {
                    String key = entry.getKey();
                    if (!key.startsWith("IMAGE_")
                            || key.startsWith("IMAGE_DESC")
                            || key.startsWith("IMAGE_PLACEHOLDER")) {
                        continue;
                    }
                    String base64Value = entry.getValue();
                    if (base64Value == null || base64Value.isEmpty()) continue;

                    String[] placeholders = {
                            "${" + key + "}",
                            "$[" + key + "]",
                            "{{" + key + "}}"
                    };
                    for (String ph : placeholders) {
                        if (text.contains(ph)) {
                            text = text.replace(ph, "");
                            run.setText("", 0);
                            try {
                                byte[] imageBytes = base64ToBytes(base64Value);
                                String suffix = detectImageSuffix(imageBytes);
                                int pictureType = getPictureType(suffix);
                                try (InputStream is = new ByteArrayInputStream(imageBytes)) {
                                    run.addPicture(is, pictureType, key,
                                            Units.toEMU(450), Units.toEMU(320));
                                }
                            } catch (Exception e) {
                                log.error("base64 图片插入失败, key={}", key, e);
                                run.setText("图片加载失败");
                                run.setColor(MANUAL_FILL_COLOR);
                            }
                            if (!text.isEmpty()) {
                                XWPFRun remainingRun = paragraph.createRun();
                                remainingRun.setText(text, 0);
                            }
                        }
                    }
                }
            }
        }
    }

    private static byte[] base64ToBytes(String base64Str) {
        return Base64.getDecoder().decode(base64Str);
    }

    private static String detectImageSuffix(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return "png";
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "png";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8 && (bytes[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46) {
            return "gif";
        }
        if (bytes[0] == 0x42 && bytes[1] == 0x4D) {
            return "bmp";
        }
        return "png";
    }

    private static void replaceImagePlaceholders(XWPFDocument document, Map<String, ImageInfo> imageData) {
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            List<XWPFRun> runs = paragraph.getRuns();
            if (runs == null) continue;

            for (int i = 0; i < runs.size(); i++) {
                XWPFRun run = runs.get(i);
                String text = run.getText(0);
                if (text == null) continue;

                for (Map.Entry<String, ImageInfo> entry : imageData.entrySet()) {
                    String key = entry.getKey();
                    ImageInfo info = entry.getValue();

                    String[] descPlaceholders = {
                            "${" + key + "_DESC}",
                            "$[" + key + "_DESC]",
                            "{{" + key + "_DESC}}"
                    };
                    for (String descPH : descPlaceholders) {
                        if (text.contains(descPH)) {
                            text = text.replace(descPH,
                                    info.getDescription() != null ? info.getDescription() : "");
                        }
                    }

                    String[] imgPlaceholders = {
                            "${" + key + "}",
                            "$[" + key + "]",
                            "{{" + key + "}}"
                    };
                    for (String imgPH : imgPlaceholders) {
                        if (text.contains(imgPH)) {
                            text = text.replace(imgPH, "");
                            run.setText("", 0);
                            try (InputStream is = new ByteArrayInputStream(info.getImageBytes())) {
                                run.addPicture(is, getPictureType(info.getSuffix()), info.getFileName(),
                                        Units.toEMU(info.getWidthMm()), Units.toEMU(info.getHeightMm()));
                            } catch (Exception e) {
                                log.error("图片插入失败, key={}", key, e);
                                run.setText("图片加载失败");
                                run.setColor(MANUAL_FILL_COLOR);
                            }
                            if (!text.isEmpty()) {
                                XWPFRun remainingRun = paragraph.createRun();
                                remainingRun.setText(text, 0);
                            }
                        }
                    }
                }
            }
        }
    }

    private static int getPictureType(String suffix) {
        if (suffix == null) return XWPFDocument.PICTURE_TYPE_PNG;
        switch (suffix.toLowerCase()) {
            case "png":  return XWPFDocument.PICTURE_TYPE_PNG;
            case "jpg": case "jpeg": return XWPFDocument.PICTURE_TYPE_JPEG;
            case "gif":  return XWPFDocument.PICTURE_TYPE_GIF;
            case "bmp":  return XWPFDocument.PICTURE_TYPE_BMP;
            default:     return XWPFDocument.PICTURE_TYPE_PNG;
        }
    }

    private static Set<String> collectAllPlaceholders(XWPFDocument document) {
        Set<String> result = new LinkedHashSet<>();
        for (XWPFParagraph paragraph : document.getParagraphs()) {
            collectPlaceholdersFromText(getParagraphFullText(paragraph), result);
        }
        for (XWPFTable table : document.getTables()) {
            for (XWPFTableRow row : table.getRows()) {
                for (XWPFTableCell cell : row.getTableCells()) {
                    for (XWPFParagraph paragraph : cell.getParagraphs()) {
                        collectPlaceholdersFromText(getParagraphFullText(paragraph), result);
                    }
                }
            }
        }
        return result;
    }

    private static void collectPlaceholdersFromText(String text, Set<String> result) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            result.add(matcher.group(0));
        }
    }

    private static String extractPlaceholderKey(Matcher matcher) {
        String key = matcher.group(1);
        if (key != null) return key;
        key = matcher.group(2);
        if (key != null) return key;
        return matcher.group(3);
    }

    private static String getParagraphFullText(XWPFParagraph paragraph) {
        StringBuilder sb = new StringBuilder();
        for (XWPFRun run : paragraph.getRuns()) {
            String text = run.getText(0);
            if (text != null) sb.append(text);
        }
        return sb.toString();
    }

    private static String getRowFullText(XWPFTableRow row) {
        StringBuilder sb = new StringBuilder();
        for (XWPFTableCell cell : row.getTableCells()) sb.append(getCellFullText(cell));
        return sb.toString();
    }

    private static String getCellFullText(XWPFTableCell cell) {
        StringBuilder sb = new StringBuilder();
        for (XWPFParagraph p : cell.getParagraphs()) sb.append(getParagraphFullText(p));
        return sb.toString();
    }

    private static void clearCellContent(XWPFTableCell cell) {
        List<XWPFParagraph> paragraphs = cell.getParagraphs();
        for (int i = paragraphs.size() - 1; i >= 0; i--) cell.removeParagraph(i);
    }

    private static void ensureRowCellCount(XWPFTableRow row, int targetCount) {
        int current = row.getTableCells().size();
        for (int i = current; i < targetCount; i++) row.createCell();
    }

    private static void copyRunStyle(XWPFRun source, XWPFRun target) {
        target.setBold(source.isBold());
        target.setItalic(source.isItalic());
        target.setStrikeThrough(source.isStrikeThrough());
        int fontSize = source.getFontSize();
        if (fontSize > 0) target.setFontSize(fontSize);
        String fontFamily = source.getFontFamily();
        if (fontFamily != null) target.setFontFamily(fontFamily);
        String color = source.getColor();
        if (color != null) target.setColor(color);
        UnderlinePatterns underline = source.getUnderline();
        if (underline != null) target.setUnderline(underline);
    }

    private static void setTextWithNewLines(XWPFRun run, String text) {
        if (text == null || text.isEmpty()) { run.setText("", 0); return; }
        try {
            if (text.contains("\n")) {
                String[] lines = text.split("\n");
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0 && !lines[i].isEmpty()) run.addCarriageReturn();
                    run.setText(lines[i], i == 0 ? 0 : -1);
                }
            } else {
                run.setText(text, 0);
            }
        } catch (XmlValueDisconnectedException e) {
            log.warn("XWPFRun 已断开连接");
        }
    }

    private static void closeQuietly(XWPFDocument doc) {
        if (doc != null) try { doc.close(); } catch (IOException ignored) {}
    }

    // ==================== ImageInfo ====================

    public static class ImageInfo {
        private byte[] imageBytes;
        private String fileName;
        private String suffix;
        private double widthMm = 120;
        private double heightMm = 80;
        private String description;

        public ImageInfo() {}
        public ImageInfo(byte[] imageBytes, String fileName, String suffix, double widthMm, double heightMm) {
            this.imageBytes = imageBytes; this.fileName = fileName; this.suffix = suffix;
            this.widthMm = widthMm; this.heightMm = heightMm;
        }
        public byte[] getImageBytes() { return imageBytes; }
        public void setImageBytes(byte[] imageBytes) { this.imageBytes = imageBytes; }
        public String getFileName() { return fileName; }
        public void setFileName(String fileName) { this.fileName = fileName; }
        public String getSuffix() { return suffix; }
        public void setSuffix(String suffix) { this.suffix = suffix; }
        public double getWidthMm() { return widthMm; }
        public void setWidthMm(double widthMm) { this.widthMm = widthMm; }
        public double getHeightMm() { return heightMm; }
        public void setHeightMm(double heightMm) { this.heightMm = heightMm; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }
}

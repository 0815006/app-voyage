package com.voyage.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voyage.entity.PerformanceResourceInfo;
import com.voyage.mapper.PerformanceResourceInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
public class PerformanceResourceInfoService extends ServiceImpl<PerformanceResourceInfoMapper, PerformanceResourceInfo> {

    private static final String UPLOAD_DIR = "/allstar/files/performanceResourceFileDir/";

    public Map<String, Object> uploadExcel(MultipartFile file) {
        Map<String, Object> resultMap = new HashMap<>();

        try {
            if (file.isEmpty()) {
                resultMap.put("result", "文件不能为空");
            }

            String originalFileName = file.getOriginalFilename();

            if (originalFileName == null) {
                resultMap.put("result", "文件名无效");
            }

            if (!isValidExcelFile(originalFileName)) {
                resultMap.put("result", "只允许上传 Excel 文件 (.xlsx, .xls)");
            }

            File directory = new File(UPLOAD_DIR);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            String fileName = generateUniqueFileName(originalFileName);
            String filePath = UPLOAD_DIR + fileName;

            resultMap.put("originalFileName", originalFileName);
            resultMap.put("fileName", fileName);
            resultMap.put("fileDir", filePath);
            resultMap.put("result", "success");

            saveFile(file, filePath);
            resultMap.put("result", "文件上传成功: " + fileName);

        } catch (Exception e) {
            log.error("文件上传失败", e);
            resultMap.put("result", "文件上传失败: " + e.getMessage());
        }
        return resultMap;
    }

    private boolean isValidExcelFile(String filename) {
        return filename.toLowerCase().endsWith(".xlsx")
                || filename.toLowerCase().endsWith(".xls");
    }

    private String generateUniqueFileName(String originalFilename) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
        String timestamp = LocalDateTime.now().format(formatter);
        String extension = originalFilename.substring(originalFilename.lastIndexOf('.'));
        String baseName = originalFilename.substring(0, originalFilename.lastIndexOf('.'));
        return baseName + "_" + timestamp + extension;
    }

    private void saveFile(MultipartFile file, String filePath) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(file.getBytes());
        }
    }

    /**
     * Excel 导入到数据库表
     */
    public void importExcel(MultipartFile file, String originalFileName, String fileName,
                            String productId, String batchNo, String userId, String fileSource) throws Exception {
        Workbook workbook;
        InputStream inputStream = file.getInputStream();
        String fileNameLowerCase = file.getOriginalFilename().toLowerCase();

        if (fileNameLowerCase.endsWith(".xls")) {
            workbook = new HSSFWorkbook(inputStream);
        } else if (fileNameLowerCase.endsWith(".xlsx")) {
            workbook = new XSSFWorkbook(inputStream);
        } else {
            throw new IllegalArgumentException("不支持的文件格式");
        }

        Sheet sheet;
        if ("资源申请表".equals(fileSource)) {
            sheet = workbook.getSheet("请将资源明细表内容复制到此sheet");
            if (sheet == null) {
                throw new IllegalArgumentException("未找到名为\"请将资源明细表内容复制到此sheet\"的页签");
            }
        } else {
            sheet = workbook.getSheetAt(0);
        }

        List<PerformanceResourceInfo> dataList = new ArrayList<>();
        Date date = new Date();

        // 跳过标题行，从第二行开始读取
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) continue;

            PerformanceResourceInfo entity = new PerformanceResourceInfo();
            int t = 1;

            entity.setSerialNumber(getIntegerCellValue(row.getCell(t)));
            entity.setTaskName(getStringCellValue(row.getCell(t + 1)));
            entity.setTaskNum(getStringCellValue(row.getCell(t + 2)));
            entity.setServiceName(getStringCellValue(row.getCell(t + 3)));
            entity.setEnglishShortName(getStringCellValue(row.getCell(t + 4)));
            entity.setBatchName(getStringCellValue(row.getCell(t + 5)));
            entity.setBusinessDept(getStringCellValue(row.getCell(t + 6)));
            entity.setProjectType(getStringCellValue(row.getCell(t + 7)));
            entity.setDisasterBackupLevel(getStringCellValue(row.getCell(t + 8)));
            entity.setAvailabilityLevel(getStringCellValue(row.getCell(t + 9)));
            entity.setDeploymentLocation(getStringCellValue(row.getCell(t + 10)));
            entity.setNetworkDeployment(getStringCellValue(row.getCell(t + 11)));
            entity.setSystemPlatform(getStringCellValue(row.getCell(t + 12)));
            entity.setPaasPlatformType(getStringCellValue(row.getCell(t + 13)));
            entity.setThemeCount(getIntegerCellValue(row.getCell(t + 14)));
            entity.setQueueCount(getIntegerCellValue(row.getCell(t + 15)));
            entity.setShardCount(getIntegerCellValue(row.getCell(t + 16)));
            entity.setPerShardCapacityGb(getIntegerCellValue(row.getCell(t + 17)));
            entity.setRedundancyMethod(getStringCellValue(row.getCell(t + 18)));
            entity.setOperatingSystem(getStringCellValue(row.getCell(t + 19)));
            entity.setMiddleware(getStringCellValue(row.getCell(t + 20)));
            entity.setPartitionUsage(getStringCellValue(row.getCell(t + 21)));
            entity.setPartitionUsageName(getStringCellValue(row.getCell(t + 22)));
            entity.setHostname(getStringCellValue(row.getCell(t + 23)));
            entity.setIpAddress(getStringCellValue(row.getCell(t + 24)));
            entity.setBackupIp(getStringCellValue(row.getCell(t + 25)));
            entity.setCpuCores(getIntegerCellValue(row.getCell(t + 26)));
            entity.setMemoryGb(getIntegerCellValue(row.getCell(t + 27)));
            entity.setDedicatedStorageGb(getIntegerCellValue(row.getCell(t + 28)));
            entity.setSharedStorageId(getStringCellValue(row.getCell(t + 29)));
            entity.setSanStorageGb(getIntegerCellValue(row.getCell(t + 30)));
            entity.setNasStorageGb(getIntegerCellValue(row.getCell(t + 31)));
            entity.setSignatureServer(getStringCellValue(row.getCell(t + 32)));
            entity.setEncryptionDevice(getStringCellValue(row.getCell(t + 33)));
            entity.setLoadBalancer(getStringCellValue(row.getCell(t + 34)));
            entity.setSslAccelerator(getStringCellValue(row.getCell(t + 35)));
            entity.setRemarks(getStringCellValue(row.getCell(t + 36)));
            entity.setPartitionRole(getStringCellValue(row.getCell(t + 37)));
            entity.setRevisionTime(getDateTimeCellValue(row.getCell(t + 38)));
            entity.setMiddlewareReasonBelowBaseline(getStringCellValue(row.getCell(t + 39)));
            entity.setOsReasonBelowBaseline(getStringCellValue(row.getCell(t + 40)));
            entity.setResourcePool(getStringCellValue(row.getCell(t + 41)));

            entity.setOriginalFileName(originalFileName);
            entity.setFileName(fileName);
            entity.setProductId(productId);
            entity.setBatchNo(batchNo);
            entity.setFileSource(fileSource);
            entity.setCreateTime(date);
            entity.setCreateOperator(userId);
            entity.setLastTime(date);
            entity.setLastOperator(userId);

            dataList.add(entity);
        }

        // 确保主键为 null，让数据库自动生成
        for (PerformanceResourceInfo entity : dataList) {
            entity.setId(null);
        }

        saveBatch(dataList);

        workbook.close();
        inputStream.close();
    }

    private String getStringCellValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getDateCellValue().toString();
                } else {
                    return String.valueOf(cell.getNumericCellValue());
                }
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return null;
        }
    }

    private Integer getIntegerCellValue(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return (int) cell.getDateCellValue().getTime();
                } else {
                    return (int) cell.getNumericCellValue();
                }
            case STRING:
                try {
                    return Integer.parseInt(cell.getStringCellValue());
                } catch (NumberFormatException e) {
                    return null;
                }
            default:
                return null;
        }
    }

    private LocalDateTime getDateTimeCellValue(Cell cell) {
        if (cell == null) return null;
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getDateCellValue().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        }
        return null;
    }
}

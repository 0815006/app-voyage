package com.voyage.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voyage.entity.MetaRefFile;
import com.voyage.mapper.MetaRefFileMapper;
import com.voyage.service.MetaRefFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class MetaRefFileServiceImpl
        extends ServiceImpl<MetaRefFileMapper, MetaRefFile>
        implements MetaRefFileService {

    @Value("${meta.ref-file-path}")
    private String refFilePath;

    @Override
    public List<MetaRefFile> listAll() {
        return lambdaQuery().orderByAsc(MetaRefFile::getRefName).list();
    }

    @Override
    public MetaRefFile uploadAndSave(String refName, String filePath, String parseType, String delimiter, String columnMapping) {
        MetaRefFile refFile = new MetaRefFile();
        refFile.setRefName(refName);
        refFile.setFilePath(filePath);
        refFile.setParseType(parseType);
        refFile.setDelimiter(delimiter);
        refFile.setColumnMapping(columnMapping);
        save(refFile);
        return refFile;
    }

    @Override
    public boolean deleteRefFile(Long id) {
        MetaRefFile refFile = getById(id);
        if (refFile == null) {
            return false;
        }
        // Delete physical file
        String filePath = refFile.getFilePath();
        if (filePath != null) {
            try {
                Files.deleteIfExists(Paths.get(filePath));
            } catch (IOException e) {
                log.error("Failed to delete file: " + filePath, e);
            }
        }
        // Delete database record
        return removeById(id);
    }

    @Override
    public List<String> previewRefFile(Long id, int lineCount) {
        MetaRefFile refFile = getById(id);
        if (refFile == null) {
            return new ArrayList<>();
        }
        String filePath = refFile.getFilePath();
        if (filePath == null) {
            return new ArrayList<>();
        }
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filePath), "UTF-8"))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < lineCount) {
                lines.add(line);
                count++;
            }
        } catch (IOException e) {
            log.error("Failed to read file: " + filePath, e);
        }
        return lines;
    }
}

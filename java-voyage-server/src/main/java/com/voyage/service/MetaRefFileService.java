package com.voyage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.voyage.entity.MetaRefFile;

import java.util.List;

public interface MetaRefFileService extends IService<MetaRefFile> {

    List<MetaRefFile> listAll();

    MetaRefFile uploadAndSave(String refName, String filePath, String parseType, String delimiter, String columnMapping);

    boolean deleteRefFile(Long id);

    List<String> previewRefFile(Long id, int lineCount);
}

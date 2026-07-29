package com.voyage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.voyage.entity.MetaEntityFile;

import java.util.List;

public interface MetaEntityFileService extends IService<MetaEntityFile> {

    List<MetaEntityFile> listByModelId(Long modelId);

    MetaEntityFile createRecord(Long modelId, String fileName, String fileType, String createUser);
}

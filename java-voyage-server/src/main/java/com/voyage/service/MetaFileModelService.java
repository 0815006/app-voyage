package com.voyage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.voyage.entity.MetaFileModel;

import java.util.List;

public interface MetaFileModelService extends IService<MetaFileModel> {

    List<MetaFileModel> listByUser(String ownerId);

    boolean saveModel(MetaFileModel model, String operator);

    boolean updateModel(MetaFileModel model, String operator);

    boolean publish(Long id, String operator);

    boolean share(Long id, String sharedWith, String operator);
}

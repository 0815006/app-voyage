package com.voyage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.voyage.entity.MetaFieldDefinition;

import java.util.List;

public interface MetaFieldDefinitionService extends IService<MetaFieldDefinition> {

    List<MetaFieldDefinition> listByModelId(Long modelId);

    void batchSave(Long modelId, List<MetaFieldDefinition> fields);

    /**
     * 校验字段合法性：定长父子长度、单向引用依赖
     */
    List<String> validateFields(Long modelId, List<MetaFieldDefinition> fields);

    /**
     * 获取Body区块中可作为SUM/COUNT目标的字段列表
     */
    List<MetaFieldDefinition> getSumTargets(Long modelId);
}

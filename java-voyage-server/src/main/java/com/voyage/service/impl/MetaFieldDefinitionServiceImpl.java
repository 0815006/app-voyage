package com.voyage.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voyage.entity.MetaFieldDefinition;
import com.voyage.mapper.MetaFieldDefinitionMapper;
import com.voyage.service.MetaFieldDefinitionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
public class MetaFieldDefinitionServiceImpl
        extends ServiceImpl<MetaFieldDefinitionMapper, MetaFieldDefinition>
        implements MetaFieldDefinitionService {

    @Override
    public List<MetaFieldDefinition> listByModelId(Long modelId) {
        return lambdaQuery()
                .eq(MetaFieldDefinition::getModelId, modelId)
                .orderByAsc(MetaFieldDefinition::getSortIndex)
                .list();
    }

    @Override
    public void batchSave(Long modelId, List<MetaFieldDefinition> fields) {
        // 空列表直接返回，不做任何操作
        if (fields == null || fields.isEmpty()) return;

        // 获取要保存的 section（从第一个字段获取）
        String section = fields.get(0).getSection();
        if (section == null || section.isEmpty()) return;

        // 只删除指定 modelId 和 section 的旧字段
        lambdaUpdate()
                .eq(MetaFieldDefinition::getModelId, modelId)
                .eq(MetaFieldDefinition::getSection, section)
                .remove();

        // 重新设置 modelId 和 sort_index（按前端传入的顺序重排 sortIndex）
        for (int i = 0; i < fields.size(); i++) {
            fields.get(i).setModelId(modelId);
            fields.get(i).setSortIndex((i + 1) * 10);
        }
        saveBatch(fields);
    }

    @Override
    public List<String> validateFields(Long modelId, List<MetaFieldDefinition> fields) {
        List<String> errors = new ArrayList<>();
        if (fields == null || fields.isEmpty()) return errors;

        // 1. 定长模式下：子字段长度之和 = 父字段长度
        Map<Long, Integer> parentLengthMap = new HashMap<>();
        Map<Long, Integer> childrenSumMap = new HashMap<>();

        for (MetaFieldDefinition f : fields) {
            if (f.getLength() == null) continue;
            if (f.getLevel() != null && f.getLevel() == 1) {
                parentLengthMap.put(f.getId(), f.getLength());
            }
            if (f.getParentId() != null) {
                childrenSumMap.merge(f.getParentId(), f.getLength(), Integer::sum);
            }
        }

        for (Map.Entry<Long, Integer> entry : parentLengthMap.entrySet()) {
            Integer childSum = childrenSumMap.get(entry.getKey());
            if (childSum != null && !childSum.equals(entry.getValue())) {
                errors.add("字段 [" + entry.getKey() + "] 长度=" + entry.getValue()
                        + "，但子字段总长度=" + childSum + "，必须相等");
            }
        }

        // 2. 单向引用校验：只能引用前面已定义的 key
        Set<String> definedKeys = new LinkedHashSet<>();
        // 预定义的虚拟变量 (Body SUM/COUNT)
        definedKeys.add("BODY_SUM_AMOUNT");
        definedKeys.add("BODY_COUNT");

        for (MetaFieldDefinition f : fields) {
            if (!definedKeys.contains(f.getFieldKey())) {
                definedKeys.add(f.getFieldKey());
            }

            // 检查 REF_FIELD 引用
            if ("REF_FIELD".equals(f.getRuleType()) && f.getRefFieldKey() != null) {
                if (!definedKeys.contains(f.getRefFieldKey())) {
                    errors.add("字段 [" + f.getFieldKey() + "] 引用了未定义或后定义的字段 [" + f.getRefFieldKey() + "]");
                }
            }
        }

        return errors;
    }

    @Override
    public List<MetaFieldDefinition> getSumTargets(Long modelId) {
        List<MetaFieldDefinition> all = listByModelId(modelId);
        List<MetaFieldDefinition> targets = new ArrayList<>();
        for (MetaFieldDefinition f : all) {
            if ("BODY".equals(f.getSection()) && "AMOUNT".equals(f.getRuleType())) {
                targets.add(f);
            }
        }
        return targets;
    }
}

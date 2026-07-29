package com.voyage.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voyage.entity.MetaFileModel;
import com.voyage.mapper.MetaFileModelMapper;
import com.voyage.service.MetaFileModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class MetaFileModelServiceImpl
        extends ServiceImpl<MetaFileModelMapper, MetaFileModel>
        implements MetaFileModelService {

    @Value("${meta.max-file-lines:200000}")
    private int maxFileLines;

    @Override
    public List<MetaFileModel> listByUser(String ownerId) {
        return lambdaQuery()
                .and(w -> w.eq(MetaFileModel::getOwnerId, ownerId)
                        .or()
                        .like(MetaFileModel::getSharedWith, ownerId))
                .orderByDesc(MetaFileModel::getUpdateTime)
                .list();
    }

    @Override
    public boolean saveModel(MetaFileModel model, String operator) {
        model.setOwnerId(operator);
        model.setStatus("DRAFT");
        model.setModelVersion(1);
        model.setCreateTime(LocalDateTime.now());
        model.setUpdateTime(LocalDateTime.now());
        if (model.getHasHeader() == null) model.setHasHeader(1);
        if (model.getHasFooter() == null) model.setHasFooter(1);
        if (model.getEncoding() == null) model.setEncoding("UTF-8");
        if (model.getMaxRowsLimit() == null) model.setMaxRowsLimit(100000);
        if (model.getMaxRowsLimit() > maxFileLines) {
            throw new IllegalArgumentException("最大行数超过全局限制: " + maxFileLines);
        }
        return save(model);
    }

    @Override
    public boolean updateModel(MetaFileModel model, String operator) {
        MetaFileModel existing = getById(model.getId());
        if (existing == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        // 允许退回草稿：PUBLISHED -> DRAFT 是允许的
        if ("PUBLISHED".equals(existing.getStatus()) && !"DRAFT".equals(model.getStatus())) {
            throw new IllegalStateException("已发布的模型请先退回草稿后再修改");
        }
        if (!existing.getOwnerId().equals(operator)) {
            // 检查是否被共享
            if (!isSharedWith(existing, operator)) {
                throw new SecurityException("无权修改该模型");
            }
        }
        if (model.getMaxRowsLimit() != null && model.getMaxRowsLimit() > maxFileLines) {
            throw new IllegalArgumentException("最大行数超过全局限制: " + maxFileLines);
        }
        model.setUpdateTime(LocalDateTime.now());
        model.setOwnerId(existing.getOwnerId()); // 防止篡改owner
        return updateById(model);
    }

    @Override
    public boolean publish(Long id, String operator) {
        MetaFileModel model = getById(id);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        if (!model.getOwnerId().equals(operator) && !isSharedWith(model, operator)) {
            throw new SecurityException("无权发布该模型");
        }
        model.setStatus("PUBLISHED");
        model.setModelVersion(model.getModelVersion() == null ? 2 : model.getModelVersion() + 1);
        model.setUpdateTime(LocalDateTime.now());
        return updateById(model);
    }

    @Override
    public boolean share(Long id, String sharedWith, String operator) {
        MetaFileModel model = getById(id);
        if (model == null) {
            throw new IllegalArgumentException("模型不存在");
        }
        if (!model.getOwnerId().equals(operator)) {
            throw new SecurityException("仅创建人可共享模型");
        }
        model.setSharedWith(sharedWith);
        model.setUpdateTime(LocalDateTime.now());
        return updateById(model);
    }

    private boolean isSharedWith(MetaFileModel model, String userId) {
        String shared = model.getSharedWith();
        if (!StringUtils.hasText(shared)) return false;
        for (String s : shared.split(",")) {
            if (s.trim().equals(userId)) return true;
        }
        return false;
    }
}

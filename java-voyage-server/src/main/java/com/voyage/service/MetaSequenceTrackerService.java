package com.voyage.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.voyage.entity.MetaSequenceTracker;

public interface MetaSequenceTrackerService extends IService<MetaSequenceTracker> {

    /**
     * 获取并递增序列号（带锁）
     */
    Long nextValue(String targetType, String targetId, boolean isPreview);

    /**
     * 重置序列号
     */
    void reset(String targetType, String targetId);
}

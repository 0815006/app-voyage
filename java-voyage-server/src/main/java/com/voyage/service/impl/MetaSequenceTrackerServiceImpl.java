package com.voyage.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voyage.entity.MetaSequenceTracker;
import com.voyage.mapper.MetaSequenceTrackerMapper;
import com.voyage.service.MetaSequenceTrackerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class MetaSequenceTrackerServiceImpl
        extends ServiceImpl<MetaSequenceTrackerMapper, MetaSequenceTracker>
        implements MetaSequenceTrackerService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long nextValue(String targetType, String targetId, boolean isPreview) {
        MetaSequenceTracker tracker = lambdaQuery()
                .eq(MetaSequenceTracker::getTargetType, targetType)
                .eq(MetaSequenceTracker::getTargetId, targetId)
                .one();

        if (tracker == null) {
            // 首次初始化
            tracker = new MetaSequenceTracker();
            tracker.setTargetType(targetType);
            tracker.setTargetId(targetId);
            tracker.setCurrentValue(0L);
            tracker.setUpdateTime(LocalDateTime.now());
            save(tracker);
        }

        long current = tracker.getCurrentValue() == null ? 0L : tracker.getCurrentValue();
        long next = current + 1;

        if (!isPreview) {
            // 正式生成：回写数据库
            tracker.setCurrentValue(next);
            tracker.setUpdateTime(LocalDateTime.now());
            updateById(tracker);
        }

        return next;
    }

    @Override
    public void reset(String targetType, String targetId) {
        MetaSequenceTracker tracker = lambdaQuery()
                .eq(MetaSequenceTracker::getTargetType, targetType)
                .eq(MetaSequenceTracker::getTargetId, targetId)
                .one();
        if (tracker != null) {
            tracker.setCurrentValue(0L);
            tracker.setUpdateTime(LocalDateTime.now());
            updateById(tracker);
        }
    }
}

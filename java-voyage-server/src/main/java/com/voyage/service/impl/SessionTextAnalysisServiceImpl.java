package com.voyage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voyage.common.BusinessException;
import com.voyage.entity.SessionTextAnalysis;
import com.voyage.mapper.SessionTextAnalysisMapper;
import com.voyage.service.SessionTextAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 文件分析文本分析会话 Service 实现。
 * <p>
 * 一个工作区一般维护一个会话（懒创建），context_messages / execution_trace
 * 以合法 JSON 文本存储（PG jsonb 列 + stringtype=unspecified 自动转换）。
 */
@Slf4j
@Service
public class SessionTextAnalysisServiceImpl
        extends ServiceImpl<SessionTextAnalysisMapper, SessionTextAnalysis>
        implements SessionTextAnalysisService {

    @Override
    public List<SessionTextAnalysis> listBySubDir(String subDirId) {
        return list(new LambdaQueryWrapper<SessionTextAnalysis>()
                .eq(SessionTextAnalysis::getSubDirId, subDirId)
                .orderByDesc(SessionTextAnalysis::getUpdateTime));
    }

    @Override
    public SessionTextAnalysis getById(String sessionId) {
        SessionTextAnalysis session = super.getById(sessionId);
        if (session == null) {
            throw new BusinessException(404, "分析会话不存在");
        }
        return session;
    }

    @Override
    @Transactional
    public SessionTextAnalysis getOrCreate(String subDirId, String title) {
        SessionTextAnalysis existing = getOne(new LambdaQueryWrapper<SessionTextAnalysis>()
                .eq(SessionTextAnalysis::getSubDirId, subDirId)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        SessionTextAnalysis session = new SessionTextAnalysis();
        session.setSessionId(UUID.randomUUID().toString());
        session.setSubDirId(subDirId);
        session.setSessionTitle(title == null || title.isBlank() ? "文件分析" : title.trim());
        session.setContextMessages("[]");
        session.setExecutionTrace("[]");
        save(session);
        log.info("创建文件分析会话: sessionId={}, subDirId={}", session.getSessionId(), subDirId);
        return getById(session.getSessionId());
    }

    @Override
    @Transactional
    public void updateContext(String sessionId, String contextJson) {
        SessionTextAnalysis session = new SessionTextAnalysis();
        session.setSessionId(sessionId);
        session.setContextMessages(contextJson);
        updateById(session);
    }

    @Override
    @Transactional
    public void updateTitle(String sessionId, String title) {
        SessionTextAnalysis session = new SessionTextAnalysis();
        session.setSessionId(sessionId);
        session.setSessionTitle(title);
        updateById(session);
    }
}

package com.voyage.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.voyage.common.BusinessException;
import com.voyage.entity.SessionDbAnalysis;
import com.voyage.mapper.SessionDbAnalysisMapper;
import com.voyage.service.SessionDbAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 数据库性能分析会话管理 Service 实现。
 */
@Slf4j
@Service
public class SessionDbAnalysisServiceImpl
        extends ServiceImpl<SessionDbAnalysisMapper, SessionDbAnalysis>
        implements SessionDbAnalysisService {

    @Override
    public List<SessionDbAnalysis> listSessions() {
        return list(new LambdaQueryWrapper<SessionDbAnalysis>()
                .orderByDesc(SessionDbAnalysis::getUpdateTime));
    }

    @Override
    public SessionDbAnalysis getSession(String id) {
        SessionDbAnalysis session = getById(id);
        if (session == null) {
            throw new BusinessException(404, "分析会话不存在");
        }
        return session;
    }

    @Override
    @Transactional
    public SessionDbAnalysis createSession(String sessionTitle, String selectedDbMeta) {
        SessionDbAnalysis session = new SessionDbAnalysis();
        session.setSessionTitle(sessionTitle);
        session.setSelectedDbMeta(selectedDbMeta);
        session.setContextMessages("[]");
        session.setExecutionTrace("[]");
        save(session);
        log.info("创建数据库分析会话: id={}, title={}", session.getId(), sessionTitle);
        return getById(session.getId());
    }

    @Override
    @Transactional
    public void updateSessionContext(String id, String messages, String traceJson) {
        SessionDbAnalysis session = new SessionDbAnalysis();
        session.setId(id);
        session.setContextMessages(messages);
        session.setExecutionTrace(traceJson);
        // 仅更新指定字段，避免覆盖已保存的元数据快照
        updateById(session);
    }

    @Override
    @Transactional
    public void updateContext(String id, String contextJson) {
        SessionDbAnalysis session = new SessionDbAnalysis();
        session.setId(id);
        session.setContextMessages(contextJson);
        updateById(session);
    }

    @Override
    @Transactional
    public void deleteSession(String id) {
        SessionDbAnalysis session = getById(id);
        if (session == null) {
            throw new BusinessException(404, "分析会话不存在");
        }
        removeById(id);
        log.info("删除数据库分析会话: id={}", id);
    }
}
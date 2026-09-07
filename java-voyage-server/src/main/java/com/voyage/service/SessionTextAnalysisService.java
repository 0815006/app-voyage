package com.voyage.service;

import com.voyage.entity.SessionTextAnalysis;

import java.util.List;

/**
 * 文件分析文本分析会话 Service 接口。
 */
public interface SessionTextAnalysisService {

    /**
     * 查询指定工作区的会话（单工作区至多一个活跃会话，按更新时间倒序）。
     */
    List<SessionTextAnalysis> listBySubDir(String subDirId);

    /**
     * 根据 ID 查询会话，不存在时抛 404。
     */
    SessionTextAnalysis getById(String sessionId);

    /**
     * 获取（或懒创建）工作区当前会话。
     */
    SessionTextAnalysis getOrCreate(String subDirId, String title);

    /**
     * 覆盖式更新会话对话上下文。
     */
    void updateContext(String sessionId, String contextJson);

    /**
     * 更新会话标题。
     */
    void updateTitle(String sessionId, String title);
}

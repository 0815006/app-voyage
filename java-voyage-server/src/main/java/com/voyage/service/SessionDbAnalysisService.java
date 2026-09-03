package com.voyage.service;

import com.voyage.entity.SessionDbAnalysis;

import java.util.List;

/**
 * 数据库性能分析会话 Service 接口。
 */
public interface SessionDbAnalysisService {

    /**
     * 查询全部会话（按更新时间倒序）。
     */
    List<SessionDbAnalysis> listSessions();

    /**
     * 根据 ID 查询会话，不存在时抛出 404。
     */
    SessionDbAnalysis getSession(String id);

    /**
     * 创建会话（保存选中 DB 元数据快照，不含密码）。
     *
     * @param sessionTitle    会话标题
     * @param selectedDbMeta 选中 DB 元数据 JSON 数组
     * @return 已保存的会话
     */
    SessionDbAnalysis createSession(String sessionTitle, String selectedDbMeta);

    /**
     * 更新会话对话上下文与执行轨迹。
     *
     * @param id          会话 ID
     * @param messages    Message 列表 JSON
     * @param traceJson   ReAct 执行轨迹 JSON
     */
    void updateSessionContext(String id, String messages, String traceJson);

    /**
     * 更新并发消息追加（原子追加方式，追加到 existing 之后）。
     *
     * @param id          会话 ID
     * @param contextJson 完整上下文 JSON（覆盖式更新）
     */
    void updateContext(String id, String contextJson);

    /**
     * 删除会话。
     */
    void deleteSession(String id);
}
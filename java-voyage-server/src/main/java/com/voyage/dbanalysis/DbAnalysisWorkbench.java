package com.voyage.dbanalysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realapex.agent.event.AgentEventListener;
import com.realapex.agent.exception.AgentSuspendedException;
import com.realapex.agent.execution.AgentRequest;
import com.realapex.agent.execution.AgentResult;
import com.realapex.agent.execution.AgentRunner;
import com.realapex.agent.execution.AgentState;
import com.realapex.agent.execution.AgentStepResult;
import com.realapex.agent.execution.ApprovalResult;
import com.realapex.agent.tool.ToolRegistry;
import com.realapex.tool.contract.AgentTool;
import com.realapex.tool.schema.SchemaGenerator;
import com.voyage.common.EmpContext;
import com.voyage.entity.SessionDbAnalysis;
import com.voyage.service.SessionDbAnalysisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * 数据库性能分析编排台（AI Workbench 编排核心）。
 * <p>
 * 负责将「前端内存凭证」注入 {@link SessionBoundDbManager} 动态连接池，
 * 通过 ai-agent-sdk 驱动 ReAct 循环（复用 {@link AgentToolSet} 多库硬隔离工具），
 * 并以 SSE 命名事件实时推送推理链 / 工具调用 / 结果。
 * <p>
 * HITL（Human-In-The-Loop）采用两段式：
 * <ol>
 *   <li>首次 {@link #run} 触发需要审批的工具时，SDK 抛 {@link AgentSuspendedException}，
 *       记录运行上下文 {@link ActiveRunContext} 到内存 {@link #activeRuns}，并推送 suspend 事件后结束当前 SSE；</li>
 *   <li>人工审批通过 {@link #resume} 在新的 SSE 连接上继续执行，连接池跨请求保留直至会话结束。</li>
 * </ol>
 */
@Slf4j
@Component
public class DbAnalysisWorkbench {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 挂起的 Agent 运行上下文（suspendId -> 上下文），resume 时据此恢复。 */
    private final ConcurrentHashMap<String, ActiveRunContext> activeRuns = new ConcurrentHashMap<>();

    private final AgentRunner agentRunner;
    private final SchemaGenerator schemaGenerator;
    private final SessionDbAnalysisService sessionService;

    public DbAnalysisWorkbench(AgentRunner agentRunner,
                               SchemaGenerator schemaGenerator,
                               SessionDbAnalysisService sessionService) {
        this.agentRunner = agentRunner;
        this.schemaGenerator = schemaGenerator;
        this.sessionService = sessionService;
    }

    /**
     * 发起一次数据库性能分析（SSE 流式）。
     *
     * @param sessionId         会话 ID
     * @param prompt            用户指令
     * @param activeConnections 前端勾选的数据库连接（含临时密码，仅存内存）
     */
    public SseEmitter run(String sessionId, String prompt, List<DbConnectionConfig> activeConnections) {
        AtomicReference<SseEmitter> emitterRef = new AtomicReference<>();
        SseEmitter emitter = new SseEmitter(1_800_000L); // 30 分钟长连接
        emitterRef.set(emitter);

        Thread.startVirtualThread(() -> {
            SessionBoundDbManager manager = new SessionBoundDbManager();
            ActiveRunContext context = new ActiveRunContext();
            context.emitterRef = emitterRef;
            context.manager = manager;
            context.sessionId = sessionId;
            try {
                // 1. 仅注册前端勾选的数据库（硬隔离边界）
                manager.registerSelectedDatabases(activeConnections);

                // 2. 构建授权别名清单注入 System Prompt（约束 Agent 只能访问勾选库）
                String authorizedAliases = activeConnections.stream()
                        .map(DbConnectionConfig::alias)
                        .collect(Collectors.joining(", ", "[", "]"));
                String systemPrompt = buildSystemPrompt(authorizedAliases);

                // 3. 组装多库隔离工具集
                AgentToolSet.DbSchemaTool schemaTool = new AgentToolSet.DbSchemaTool(manager);
                AgentToolSet.ExplainTool explainTool = new AgentToolSet.ExplainTool(manager);
                AgentToolSet.DbQueryTool queryTool = new AgentToolSet.DbQueryTool(manager);
                ToolRegistry registry = new ToolRegistry(schemaGenerator);
                registry.register(schemaTool);
                registry.register(explainTool);
                registry.register(queryTool);

                // 4. 回载历史上下文（持久化的精简历史）
                String historyContext = loadHistoryContext(sessionId);

                // 5. 执行 ReAct 循环（SSE 命名事件流式推送）
                final SseEmitter currentEmitter = emitterRef.get();
                agentRunner.run(AgentRequest.builder()
                        .systemPrompt(systemPrompt)
                        .userPrompt(prompt + composeHistory(historyContext))
                        .tools(registry.getAll())
                        .maxSteps(10)
                        .listener(new AgentEventListener() {
                            @Override
                            public void onStepStart(int step) {
                                sendSseEvent(currentEmitter, "step_start", Map.of("step", step));
                            }

                            @Override
                            public void onChunk(String textChunk) {
                                sendSseData(currentEmitter, "thought_chunk", textChunk);
                            }

                            @Override
                            public void onToolStart(String toolName, String toolInputJson) {
                                sendSseEvent(currentEmitter, "tool_start",
                                        Map.of("name", toolName, "args", toolInputJson));
                            }

                            @Override
                            public void onToolEnd(String toolName, Object toolOutput) {
                                String output = toolOutput != null ? toolOutput.toString() : "(无输出)";
                                sendSseEvent(currentEmitter, "tool_end", Map.of(
                                        "name", toolName,
                                        "output", output,
                                        "length", output.length(),
                                        "truncated", output.length() > 800
                                ));
                            }

                            @Override
                            public void onStepFinish(AgentStepResult stepResult) {
                                log.debug("[DB分析] Step {} 完成, 耗时: {}ms",
                                        stepResult.getStepNumber(), stepResult.getDurationMs());
                            }

                            @Override
                            public void onComplete(AgentResult result) {
                                if (result.getFinalText() != null && !result.getFinalText().isEmpty()) {
                                    sendSseData(currentEmitter, "text_chunk", result.getFinalText());
                                }
                                persistCompletion(sessionId, prompt, result.getFinalText());
                                sendSseEvent(currentEmitter, "complete", Map.of(
                                        "finalText", result.getFinalText(),
                                        "totalSteps", result.getTotalSteps(),
                                        "totalTokens", result.getTotalTokens(),
                                        "totalDurationMs", result.getTotalDurationMs()
                                ));
                                currentEmitter.complete();
                                // 会话正常结束，释放连接池
                                manager.close();
                            }
                        })
                        .build());

            } catch (AgentSuspendedException se) {
                AgentState state = se.getAgentState();
                handleSuspend(context, state);
            } catch (Exception e) {
                log.error("[DB分析] 执行异常: {}", e.getMessage(), e);
                manager.close();
                sendSseData(emitterRef.get(), "error", e.getMessage());
                emitterRef.get().complete();
            }
        });

        return emitter;
    }

    /**
     * 人工审批后恢复挂起的 Agent 执行（SSE 流式）。
     *
     * @param suspendId 挂起唯一 ID
     * @param approved  是否批准
     * @param comment   审批意见
     */
    public SseEmitter resume(String suspendId, boolean approved, String comment) {
        ActiveRunContext context = activeRuns.get(suspendId);
        if (context == null) {
            throw new IllegalArgumentException("审批上下文不存在或已过期: " + suspendId);
        }
        SseEmitter emitter = new SseEmitter(1_800_000L);
        context.emitterRef.set(emitter); // 重定向到新的 SSE 连接

        Thread.startVirtualThread(() -> {
            try {
                String operator = EmpContext.getEmpNo();
                ApprovalResult approval = approved
                        ? ApprovalResult.approve(operator, comment)
                        : ApprovalResult.reject(operator, comment);

                AgentResult result = agentRunner.resume(suspendId, approval);
                SseEmitter active = context.emitterRef.get();
                if (result.getFinalText() != null && !result.getFinalText().isEmpty()) {
                    sendSseData(active, "text_chunk", result.getFinalText());
                }
                sendSseEvent(active, "complete", Map.of(
                        "finalText", result.getFinalText(),
                        "totalSteps", result.getTotalSteps(),
                        "totalTokens", result.getTotalTokens(),
                        "totalDurationMs", result.getTotalDurationMs()
                ));
                active.complete();
            } catch (AgentSuspendedException se) {
                // 审批后再触发新一轮审批，更新挂起状态继续等待
                handleSuspend(context, se.getAgentState());
                return;
            } catch (Exception e) {
                log.error("[DB分析] resume 异常: {}", e.getMessage(), e);
                sendSseData(context.emitterRef.get(), "error", e.getMessage());
            } finally {
                // 无论结果，本轮 resume 结束后释放会话级连接池并清理挂起上下文
                context.manager.close();
                activeRuns.remove(suspendId);
                context.emitterRef.get().complete();
            }
        });

        return emitter;
    }

    // ==================== 内部逻辑 ====================

    /** 挂起处理：记录上下文到内存 Map，推送 suspend 事件后结束当前 SSE 连接。 */
    private void handleSuspend(ActiveRunContext context, AgentState state) {
        context.suspendId = state.getSuspendId();
        List<Map<String, String>> pendingTools = state.getPendingToolCalls() == null
                ? List.of()
                : state.getPendingToolCalls().stream()
                        .map(tc -> Map.of(
                                "name", tc.getName() == null ? "" : tc.getName(),
                                "args", tc.getArguments() == null ? "{}" : tc.getArguments()))
                        .collect(Collectors.toList());
        activeRuns.put(state.getSuspendId(), context);
        log.warn("[DB分析] HITL 挂起: suspendId={}, pendingTools={}",
                state.getSuspendId(), pendingTools);
        sendSseEvent(context.emitterRef.get(), "suspend", Map.of(
                "suspendId", state.getSuspendId(),
                "pendingTools", pendingTools
        ));
        context.emitterRef.get().complete();
    }

    /** 依据勾选别名动态生成约束性 System Prompt。 */
    private String buildSystemPrompt(String authorizedAliases) {
        return """
                你是一个专业的数据库性能诊断专家。
                【严格安全约束】当前用户在 UI 界面中仅勾选并授权了以下数据库连接：
                %s

                规则：
                1. 你调用的任何数据库工具，必须指定 target_alias 参数，且必须完全匹配上述授权别名之一。
                2. 【选中即允许、未选严禁访问】严禁猜测或尝试访问未在上述列表中的数据库。
                3. 若用户提及未勾选的数据库，必须明确告知该库未被授权，无法访问。
                4. 可调用的工具：
                   - get_db_schema: 获取指定授权库某表的结构/索引/主外键
                   - explain_sql: 获取指定授权库上 SQL 的 EXPLAIN 执行计划
                   - readonly_query: 在指定授权库执行只读 SELECT/SHOW/DESCRIBE/EXPLAIN 查询
                5. 多库对比时，按 target_alias 分别调用工具再横向对比输出结论。
                """.formatted(authorizedAliases == null ? "[]" : authorizedAliases);
    }

    /** 从会话读取历史上下文（供同一会话续问时追加）。 */
    private String loadHistoryContext(String sessionId) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                return "";
            }
            SessionDbAnalysis session = sessionService.getSession(sessionId);
            String raw = session.getContextMessages();
            if (raw == null || raw.isBlank()) {
                return "";
            }
            return raw;
        } catch (Exception e) {
            log.debug("[DB分析] 回载历史失败: {}", e.getMessage());
            return "";
        }
    }

    /** 将历史以补丁形式拼接到 prompt 之后（精简实现：直接呈现先前结论）。 */
    private String composeHistory(String historyContext) {
        if (historyContext == null || historyContext.isBlank() || "[]".equals(historyContext)) {
            return "";
        }
        return "\n\n【会话历史参考】\n" + historyContext + "\n（以上为历史记录，可作为上下文参考）";
    }

    /** 会话结束后持久化最终结果到 context_messages。 */
    private void persistCompletion(String sessionId, String prompt, String finalText) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                return;
            }
            String runDoc = "【用户指令】" + prompt + "\n【分析结论】" + (finalText == null ? "(无)" : finalText);
            sessionService.updateContext(sessionId, runDoc);
        } catch (Exception e) {
            log.warn("[DB分析] 持久化会话结论失败: {}", e.getMessage());
        }
    }

    // ==================== SSE 发送工具方法 ====================

    private void sendSseEvent(SseEmitter emitter, String eventName, Object data) {
        if (emitter == null) {
            return;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (Exception e) {
            log.debug("SSE 发送失败 (客户端可能已断开): {}", e.getMessage());
        }
    }

    private void sendSseData(SseEmitter emitter, String eventName, String text) {
        sendSseEvent(emitter, eventName, text);
    }

    /**
     * 挂起运行上下文（resume 时从内存 Map 恢复）。
     * <p>emitterRef 为可重定向引用，使首次 run 建立的 listener
     * 在 resume 时能推送到新的 SSE 连接。</p>
     */
    public static final class ActiveRunContext {
        public String suspendId;
        public String sessionId;
        public SessionBoundDbManager manager;
        public AtomicReference<SseEmitter> emitterRef;
    }
}
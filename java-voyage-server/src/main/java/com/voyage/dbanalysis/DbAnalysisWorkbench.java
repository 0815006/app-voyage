package com.voyage.dbanalysis;

import com.fasterxml.jackson.core.type.TypeReference;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
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

        // 连接生命周期标志：normal complete / 客户端断开 / 超时 / 发送失败 后置 true。
        // 置 true 后不再向已结束的 SSE 连接写入事件（后台 Agent 仍会跑完以完成会话持久化），
        // 避免客户端断开后模型持续流式输出导致的 "ResponseBodyEmitter has already completed" 日志刷屏。
        AtomicBoolean emitterClosed = new AtomicBoolean(false);
        emitter.onCompletion(() -> emitterClosed.set(true));
        emitter.onTimeout(() -> emitterClosed.set(true));
        emitter.onError(e -> emitterClosed.set(true));

        Thread.startVirtualThread(() -> {
            SessionBoundDbManager manager = new SessionBoundDbManager();
            ActiveRunContext context = new ActiveRunContext();
            context.emitterRef = emitterRef;
            context.emitterClosed = emitterClosed;
            context.manager = manager;
            context.sessionId = sessionId;
            try {
                boolean hasDatabases = activeConnections != null && !activeConnections.isEmpty();

                // 1. 仅注册前端勾选的数据库（硬隔离边界）；无库时跳过，进入「无库咨询模式」
                if (hasDatabases) {
                    manager.registerSelectedDatabases(activeConnections);
                }

                // 2. 构建 System Prompt：有库走授权约束，无库走咨询模式提示（不虚构访问）
                String systemPrompt;
                ToolRegistry registry = new ToolRegistry(schemaGenerator);
                if (hasDatabases) {
                    String authorizedAliases = activeConnections.stream()
                            .map(DbConnectionConfig::alias)
                            .collect(Collectors.joining(", ", "[", "]"));
                    systemPrompt = buildSystemPrompt(authorizedAliases);

                    // 3. 组装多库隔离工具集
                    AgentToolSet.DbSchemaTool schemaTool = new AgentToolSet.DbSchemaTool(manager);
                    AgentToolSet.ExplainTool explainTool = new AgentToolSet.ExplainTool(manager);
                    AgentToolSet.DbQueryTool queryTool = new AgentToolSet.DbQueryTool(manager);
                    registry.register(schemaTool);
                    registry.register(explainTool);
                    registry.register(queryTool);
                } else {
                    log.info("[DB分析] 无库咨询模式：sessionId={} 未绑定目标数据库，仅文本咨询，不注册任何数据库工具", sessionId);
                    systemPrompt = buildConsultingSystemPrompt();
                }

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
                                sendSseEvent(currentEmitter, context.emitterClosed, "step_start", Map.of("step", step));
                            }

                            @Override
                            public void onChunk(String textChunk) {
                                sendSseData(currentEmitter, context.emitterClosed, "thought_chunk", textChunk);
                            }

                            @Override
                            public void onToolStart(String toolName, String toolInputJson) {
                                sendSseEvent(currentEmitter, context.emitterClosed, "tool_start",
                                        Map.of("name", toolName, "args", toolInputJson));
                            }

                            @Override
                            public void onToolEnd(String toolName, Object toolOutput) {
                                String output = toolOutput != null ? toolOutput.toString() : "(无输出)";
                                sendSseEvent(currentEmitter, context.emitterClosed, "tool_end", Map.of(
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
                                // 无论客户端是否已断开，本轮结论都持久化到会话，用户可在历史中查看
                                persistCompletion(sessionId, prompt, result.getFinalText());
                                if (context.emitterClosed.get()) {
                                    manager.close();
                                    return;
                                }
                                if (result.getFinalText() != null && !result.getFinalText().isEmpty()) {
                                    sendSseData(currentEmitter, context.emitterClosed, "text_chunk", result.getFinalText());
                                }
                                sendSseEvent(currentEmitter, context.emitterClosed, "complete", Map.of(
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
                if (!context.emitterClosed.get()) {
                    sendSseData(emitterRef.get(), context.emitterClosed, "error", e.getMessage());
                    try {
                        emitterRef.get().complete();
                    } catch (Exception ignore) {
                        // 连接已结束，忽略
                    }
                }
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
        // 旧 SSE 连接已结束，重置关闭标志并绑定到新的 SSE 连接
        context.emitterClosed.set(false);
        SseEmitter emitter = new SseEmitter(1_800_000L);
        context.emitterRef.set(emitter); // 重定向到新的 SSE 连接
        emitter.onCompletion(() -> context.emitterClosed.set(true));
        emitter.onTimeout(() -> context.emitterClosed.set(true));
        emitter.onError(e -> context.emitterClosed.set(true));

        Thread.startVirtualThread(() -> {
            try {
                String operator = EmpContext.getEmpNo();
                ApprovalResult approval = approved
                        ? ApprovalResult.approve(operator, comment)
                        : ApprovalResult.reject(operator, comment);

                AgentResult result = agentRunner.resume(suspendId, approval);
                SseEmitter active = context.emitterRef.get();
                if (context.emitterClosed.get()) {
                    return;
                }
                if (result.getFinalText() != null && !result.getFinalText().isEmpty()) {
                    sendSseData(active, context.emitterClosed, "text_chunk", result.getFinalText());
                }
                sendSseEvent(active, context.emitterClosed, "complete", Map.of(
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
                if (!context.emitterClosed.get()) {
                    sendSseData(context.emitterRef.get(), context.emitterClosed, "error", e.getMessage());
                }
            } finally {
                // 无论结果，本轮 resume 结束后释放会话级连接池并清理挂起上下文
                context.manager.close();
                activeRuns.remove(suspendId);
                if (!context.emitterClosed.get()) {
                    try {
                        context.emitterRef.get().complete();
                    } catch (Exception ignore) {
                        // 连接已结束，忽略
                    }
                }
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
        sendSseEvent(context.emitterRef.get(), context.emitterClosed, "suspend", Map.of(
                "suspendId", state.getSuspendId(),
                "pendingTools", pendingTools
        ));
        try {
            context.emitterRef.get().complete();
        } catch (Exception ignore) {
            // 连接已结束，忽略
        }
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

    /** 无库咨询模式提示词：本次会话未绑定任何目标数据库（纯咨询 / SQL 设计），无任何数据库工具可调用。 */
    private String buildConsultingSystemPrompt() {
        return """
                你是一个资深的数据库架构师与 SQL 专家，当前处于「无库咨询模式」——本次会话未绑定任何目标数据库连接。
                规则：
                1. 你没有可调用的任何数据库探查 / 查询 / 执行计划工具，禁止声称已连接、查询或验证过某个真实数据库。
                2. 请基于用户提供的业务逻辑与描述，直接输出可落地的成果：建表 DDL、SQL 语句设计、索引建议、查询优化思路等。
                3. 未明确数据库方言时，默认同时给出 MySQL / PostgreSQL 的差异说明（必要时补充 GaussDB），帮助用户在不同目标库落地。
                4. 若用户的需求必须真实连接数据库（如探查表结构、EXPLAIN 验证、数据对比），应明确告知：当前未绑定数据库，
                   请点击输入框左侧 ＋ 号选择并授权目标库后再发起。
                """;
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

    /** 将历史消息 JSON 数组转成可读的「历史对话」补丁，拼接到当前 prompt 之后。 */
    private String composeHistory(String historyContext) {
        if (historyContext == null || historyContext.isBlank() || "[]".equals(historyContext)) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n【会话历史参考】\n");
        List<Map<String, String>> messages = parseContextMessages(historyContext);
        if (messages.isEmpty()) {
            // 容错：历史遗留非结构化文本，原样作为参考
            sb.append(historyContext);
        } else {
            for (Map<String, String> msg : messages) {
                String role = msg.get("role");
                String content = msg.get("content");
                if (content == null || content.isBlank()) {
                    continue;
                }
                String who = "user".equals(role) ? "用户" : ("assistant".equals(role) ? "AI" : String.valueOf(role));
                sb.append(who).append('：').append(content).append('\n');
            }
        }
        sb.append("\n（以上为历史记录，可作为上下文参考）");
        return sb.toString();
    }

    /**
     * 会话结束后，将本轮「用户指令 + 分析结论」以结构化 Message 追加到 context_messages（JSONB Message 列表）。
     * 追加而非覆盖，保证多轮对话历史随会话累积；写入前序列化为合法 JSON，避免 jsonb 列拒绝非 JSON 纯文本。
     */
    private void persistCompletion(String sessionId, String prompt, String finalText) {
        try {
            if (sessionId == null || sessionId.isBlank()) {
                return;
            }
            SessionDbAnalysis session = sessionService.getSession(sessionId);
            List<Map<String, String>> history = parseContextMessages(session.getContextMessages());
            history.add(Map.of("role", "user", "content", prompt == null ? "" : prompt));
            history.add(Map.of("role", "assistant", "content", finalText == null ? "(无)" : finalText));
            sessionService.updateContext(sessionId, OBJECT_MAPPER.writeValueAsString(history));
        } catch (Exception e) {
            log.warn("[DB分析] 持久化会话结论失败: {}", e.getMessage());
        }
    }

    /** 解析 context_messages JSONB（Message 数组）；空或非 JSON（历史遗留文本）时返回空列表。 */
    private List<Map<String, String>> parseContextMessages(String raw) {
        if (raw == null || raw.isBlank()) {
            return new ArrayList<>();
        }
        try {
            List<Map<String, String>> list = OBJECT_MAPPER.readValue(
                    raw, new TypeReference<List<Map<String, String>>>() {
                    });
            return list == null ? new ArrayList<>() : new ArrayList<>(list);
        } catch (Exception e) {
            log.debug("[DB分析] context_messages 非 JSON Message 数组，忽略: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== SSE 发送工具方法 ====================

    private void sendSseEvent(SseEmitter emitter, AtomicBoolean closed, String eventName, Object data) {
        if (emitter == null || (closed != null && closed.get())) {
            return; // 连接已结束，静默丢弃，避免持续刷屏
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (Exception e) {
            // 客户端断开 / 响应已提交导致发送失败：标记连接关闭并降噪
            if (closed != null) {
                closed.set(true);
            }
            log.debug("SSE 发送失败 (客户端可能已断开): {}", e.getMessage());
        }
    }

    private void sendSseData(SseEmitter emitter, AtomicBoolean closed, String eventName, String text) {
        sendSseEvent(emitter, closed, eventName, text);
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
        /** SSE 连接生命周期标志：complete / 断开 / 超时 / 发送失败后置 true。 */
        public AtomicBoolean emitterClosed = new AtomicBoolean(false);
    }
}
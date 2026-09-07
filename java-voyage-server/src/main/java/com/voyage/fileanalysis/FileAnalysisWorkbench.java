package com.voyage.fileanalysis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realapex.agent.event.AgentEventListener;
import com.realapex.agent.execution.AgentRequest;
import com.realapex.agent.execution.AgentResult;
import com.realapex.agent.execution.AgentRunner;
import com.realapex.agent.execution.AgentStepResult;
import com.realapex.agent.tool.ToolRegistry;
import com.realapex.tool.base.BaseToolConfig;
import com.realapex.tool.base.BaseToolFactory;
import com.realapex.tool.doc.config.DocToolConfig;
import com.realapex.tool.doc.config.DocToolFactory;
import com.voyage.common.BusinessException;
import com.voyage.entity.SessionTextAnalysis;
import com.voyage.entity.WorkspaceFile;
import com.voyage.entity.WorkspaceSubDir;
import com.voyage.service.SessionTextAnalysisService;
import com.voyage.service.WorkspaceFileService;
import com.voyage.service.WorkspaceSubDirService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 文件分析（Document Studio）编排台 —— 文件分析 Agent 核心集成。
 * <p>
 * 以「当前激活的工作区（物理隔离子目录）」为沙箱边界：每轮对话按工作区
 * {@code abs_path} 动态构建沙箱配置，注册 ai-tool-doc 文档工具
 * （read_and_convert_doc / inspect_template_schema / render_document）与
 * ai-tool-sdk 基础文件系统工具（writeMarkdown / writeFile / readFile / editFile 等），
 * 工具内部 PathSafety 二次校验阻断 ../ 路径穿越。
 * <p>
 * 会话上下文持久化于 biz_session_text_analysis，Agent 渲染生成的报告在工作区结束
 * 时由文件服务同步注册为 biz_workspace_file，前端可下载 / 预览。
 */
@Slf4j
@Component
public class FileAnalysisWorkbench {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final AgentRunner agentRunner;
    private final com.realapex.tool.schema.SchemaGenerator schemaGenerator;
    private final WorkspaceSubDirService workspaceService;
    private final WorkspaceFileService fileService;
    private final SessionTextAnalysisService sessionService;

    public FileAnalysisWorkbench(AgentRunner agentRunner,
                                 com.realapex.tool.schema.SchemaGenerator schemaGenerator,
                                 WorkspaceSubDirService workspaceService,
                                 WorkspaceFileService fileService,
                                 SessionTextAnalysisService sessionService) {
        this.agentRunner = agentRunner;
        this.schemaGenerator = schemaGenerator;
        this.workspaceService = workspaceService;
        this.fileService = fileService;
        this.sessionService = sessionService;
    }

    /**
     * 发起一轮文件分析对话（SSE 流式）。
     *
     * @param empNo    操作员工号
     * @param subDirId 当前激活工作区 UUID
     * @param prompt   用户指令
     */
    public SseEmitter run(String empNo, String subDirId, String prompt) {
        SseEmitter emitter = new SseEmitter(1_800_000L); // 30 分钟长连接
        AtomicReference<SseEmitter> emitterRef = new AtomicReference<>(emitter);
        AtomicBoolean emitterClosed = new AtomicBoolean(false);
        emitter.onCompletion(() -> emitterClosed.set(true));
        emitter.onTimeout(() -> emitterClosed.set(true));
        emitter.onError(e -> emitterClosed.set(true));

        // 记录本轮开始时刻：结束后同步该时刻之后出现的 Agent 渲染产物
        final long runStartEpoch = System.currentTimeMillis();

        Thread.startVirtualThread(() -> {
            try {
                // 1. 工作区归属校验 + 沙箱配置
                WorkspaceSubDir ws = workspaceService.getById(subDirId);
                if (!ws.getEmpNo().equals(empNo)) {
                    throw new BusinessException(403, "无权访问该工作区");
                }
                Path baseDir = Path.of(ws.getAbsPath()).toAbsolutePath().normalize();
                if (!Files.isDirectory(baseDir)) {
                    throw new BusinessException(500, "工作区物理目录缺失: " + baseDir);
                }

                // 2. 会话（懒创建 + 回载历史）
                SessionTextAnalysis session = sessionService.getOrCreate(subDirId, promptToTitle(prompt));
                String historyContext = loadHistoryContext(session);

                // 3. 文件清单（作为工具可用上下文）
                List<WorkspaceFile> files = fileService.listBySubDir(empNo, subDirId);
                StringBuilder fileManifest = buildFileManifest(files);

                // 4. 构建当前工作区沙箱工具集（动态注册 ai-tool-doc 文档工具 + ai-tool-sdk 基础文件系统工具）
                DocToolConfig config = DocToolConfig.builder()
                        .baseDir(baseDir)
                        // 输出目录指向工作区根目录，便于「根目录扫描」将渲染产物注册为工作区文件
                        .outputDir(baseDir)
                        .build();
                ToolRegistry registry = new ToolRegistry(schemaGenerator);
                DocToolFactory.createDocTools(config).forEach(registry::register);
                // 装配 ai-tool-sdk 文件系统工具组（writeMarkdown / writeFile / readFile / editFile /
                // readDir / getTree / makeDir / searchFiles / searchContent），使 Agent 能直接把
                // Markdown/文本报告写入当前工作区，写入产物由 syncGeneratedFiles 同步为工作区文件。
                BaseToolConfig baseConfig = BaseToolConfig.builder()
                        .baseDir(baseDir)
                        .build();
                BaseToolFactory.createFileSystemTools(baseConfig).forEach(registry::register);
                log.info("[文件分析] 会话 subDirId={}, 工具={}, 文件数={}",
                        subDirId, registry.getToolDefinitions().size(), files.size());

                String systemPrompt = buildSystemPrompt(ws.getDirName(), baseDir.toString());

                final SseEmitter currentEmitter = emitterRef.get();
                agentRunner.run(AgentRequest.builder()
                        .systemPrompt(systemPrompt)
                        .userPrompt(prompt + composeHistory(historyContext) + fileManifest)
                        .tools(registry.getAll())
                        .maxSteps(10)
                        .listener(new AgentEventListener() {
                            @Override
                            public void onStepStart(int step) {
                                sendSseEvent(currentEmitter, emitterClosed, "step_start",
                                        Map.of("step", step));
                            }

                            @Override
                            public void onChunk(String textChunk) {
                                sendSseEvent(currentEmitter, emitterClosed, "thought_chunk", textChunk);
                            }

                            @Override
                            public void onToolStart(String toolName, String toolInputJson) {
                                sendSseEvent(currentEmitter, emitterClosed, "tool_start",
                                        Map.of("name", toolName, "args", toolInputJson));
                            }

                            @Override
                            public void onToolEnd(String toolName, Object toolOutput) {
                                String output = toolOutput != null ? toolOutput.toString() : "(无输出)";
                                sendSseEvent(currentEmitter, emitterClosed, "tool_end", Map.of(
                                        "name", toolName,
                                        "output", output,
                                        "length", output.length(),
                                        "truncated", output.length() > 800));
                            }

                            @Override
                            public void onStepFinish(AgentStepResult stepResult) {
                                log.debug("[文件分析] Step {} 完成, 耗时: {}ms",
                                        stepResult.getStepNumber(), stepResult.getDurationMs());
                            }

                            @Override
                            public void onComplete(AgentResult result) {
                                // 1. 追加本轮对话到会话上下文（无论客户端是否断开）
                                persistCompletion(session, prompt, result.getFinalText());

                                // 2. 同步 Agent 渲染生成的报告为工作区文件
                                List<WorkspaceFile> generated;
                                try {
                                    generated = fileService.syncGeneratedFiles(empNo, subDirId, runStartEpoch);
                                } catch (Exception e) {
                                    log.warn("[文件分析] 同步渲染产物失败: {}", e.getMessage());
                                    generated = List.of();
                                }

                                if (emitterClosed.get()) {
                                    return;
                                }
                                if (result.getFinalText() != null && !result.getFinalText().isEmpty()) {
                                    sendSseEvent(currentEmitter, emitterClosed, "text_chunk", result.getFinalText());
                                }
                                sendSseEvent(currentEmitter, emitterClosed, "complete", Map.of(
                                        "finalText", result.getFinalText(),
                                        "totalSteps", result.getTotalSteps(),
                                        "totalTokens", result.getTotalTokens(),
                                        "totalDurationMs", result.getTotalDurationMs(),
                                        "sessionId", session.getSessionId(),
                                        "generatedFiles", generated.stream().map(WorkspaceFile::getFileId).toList()
                                ));
                                currentEmitter.complete();
                            }
                        })
                        .build());

            } catch (Exception e) {
                log.error("[文件分析] 执行异常: {}", e.getMessage(), e);
                // 兜底：Agent 执行异常/断流时，只要工具已产生落盘产物，仍按时间窗同步注册为工作区文件，
                // 避免出现「磁盘有文件、数据库无记录」的失配（用户也可通过文件树刷新按钮二次对账）。
                try {
                    fileService.syncGeneratedFiles(empNo, subDirId, runStartEpoch);
                } catch (Exception syncEx) {
                    log.warn("[文件分析] 异常兜底同步渲染产物失败: {}", syncEx.getMessage());
                }
                if (!emitterClosed.get()) {
                    sendSseEvent(emitterRef.get(), emitterClosed, "error", e.getMessage());
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

    // ==================== 内部逻辑 ====================

    /** 从首条用户指令截取会话标题。 */
    private String promptToTitle(String prompt) {
        if (prompt == null) {
            return "文件分析";
        }
        String oneLine = prompt.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 24 ? oneLine.substring(0, 24) : (oneLine.isEmpty() ? "文件分析" : oneLine);
    }

    /** 构建约束当前工作区沙箱边界的 System Prompt。 */
    private String buildSystemPrompt(String dirName, String absPath) {
        return """
                你是「文档工作台」(Document Studio) 的文件分析专家，基于物理隔离的工作区完成
                文档质检、模板重构、合同对比、多格式长文本问答与分析。

                【当前工作区】
                展示名称: %s
                沙箱绝对路径: %s

                【可调用工具】
                （一）文档分析（ai-tool-doc）
                - read_and_convert_doc: 将当前工作区内的 Word/Excel/PDF 文档转换为结构化 Markdown。
                  入参 source 必须使用【文件名】或相对当前工作区的相对路径，禁止使用绝对路径。
                - inspect_template_schema: 探查 .docx/.xlsx 模板的占位符变量 Schema。
                - render_document: 将结构化数据(JSON)渲染进 .docx/.xlsx 模板生成 Word/Excel 报告，
                  生成文件自动落入当前工作区（用户可在文件树中下载）。
                （二）文件系统基础能力（ai-tool-sdk）
                - writeMarkdown: 将结构化 Markdown 直接写入当前工作区（自动建父目录），
                  最适合把分析结论/质检报告落盘为 .md 文件。相对路径或文件名均可。
                - writeFile / editFile / readFile: 通用文本文件写入、就地编辑、读取。
                - makeDir / readDir / getTree / searchFiles / searchContent: 目录与内容检索。
                  生成的 .md / .txt 等文本文件会在本轮结束后自动同步到当前工作区文件树，可下载。

                【安全规则】
                1. 所有工具只能操作当前工作区内的文件；工作区外文件一律拒绝，禁止尝试 ../ 穿越。
                2. 引用文件时以用户提供的文件清单中的文件名为准，不要臆造不存在的文件。
                3. 若用户要求检查占位符残留/模板变量一致性，优先 read_and_convert_doc 解析内容，
                   结合 inspect_template_schema 比对模板 Schema 后给出结论。
                4. 需要产出分析/质检报告时：用户指定或适合 Markdown 报告时，使用 writeMarkdown
                   直接写入 .md 并告知用户已生成到当前工作区文件树（可下载/预览）；只有用户
                   明确要求 Word/Excel 正式交付件时，才使用 render_document 依据模板渲染。
                """.formatted(dirName, absPath);
    }

    /** 从会话读取历史上下文（供同一会话续问）。 */
    private String loadHistoryContext(SessionTextAnalysis session) {
        String raw = session.getContextMessages();
        return raw == null || raw.isBlank() ? "" : raw;
    }

    /** 将历史消息 JSON 数组转成「历史对话」补丁。 */
    private String composeHistory(String historyContext) {
        if (historyContext == null || historyContext.isBlank() || "[]".equals(historyContext)) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n\n【会话历史参考】\n");
        List<Map<String, String>> messages = parseContextMessages(historyContext);
        if (messages.isEmpty()) {
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

    /** 将当前工作区文件清单拼入 prompt（含 parsedMarkdown 摘要，供 Agent 直接问答）。 */
    private StringBuilder buildFileManifest(List<WorkspaceFile> files) {
        StringBuilder sb = new StringBuilder("\n\n【当前工作区文件清单】\n");
        if (files == null || files.isEmpty()) {
            sb.append("（暂无文件。若用户需求必须基于真实文档，请明确告知用户先上传文件。）");
            return sb;
        }
        sb.append("文件名\t类型\t大小(B)\n");
        for (WorkspaceFile f : files) {
            sb.append(f.getFileName()).append('\t')
                    .append(f.getFileType()).append('\t')
                    .append(f.getFileSize() == null ? 0 : f.getFileSize()).append('\n');
        }
        sb.append("\n说明：如需全文理解某文件，请调用 read_and_convert_doc（source=文件名）获取内容。");
        return sb;
    }

    /** 会话结束后将「用户指令 + AI 结论」追加到 context_messages。 */
    private void persistCompletion(SessionTextAnalysis session, String prompt, String finalText) {
        try {
            List<Map<String, String>> history = parseContextMessages(session.getContextMessages());
            history.add(Map.of("role", "user", "content", prompt == null ? "" : prompt));
            history.add(Map.of("role", "assistant", "content", finalText == null ? "(无)" : finalText));
            sessionService.updateContext(session.getSessionId(), OBJECT_MAPPER.writeValueAsString(history));
            // 首问时标题已截取，无需再次回写
        } catch (Exception e) {
            log.warn("[文件分析] 持久化会话结论失败: {}", e.getMessage());
        }
    }

    /** 解析 context_messages JSONB（Message 数组）；空或非 JSON 时返回空列表。 */
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
            log.debug("[文件分析] context_messages 非 JSON Message 数组，忽略: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    // ==================== SSE 发送工具方法 ====================

    private void sendSseEvent(SseEmitter emitter, AtomicBoolean closed, String eventName, Object data) {
        if (emitter == null || (closed != null && closed.get())) {
            return;
        }
        try {
            String json = OBJECT_MAPPER.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (Exception e) {
            if (closed != null) {
                closed.set(true);
            }
            log.debug("SSE 发送失败 (客户端可能已断开): {}", e.getMessage());
        }
    }
}

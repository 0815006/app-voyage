package com.voyage.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realapex.agent.event.AgentEventListener;
import com.realapex.agent.execution.AgentRequest;
import com.realapex.agent.execution.AgentResult;
import com.realapex.agent.execution.AgentRunner;
import com.realapex.agent.execution.AgentStepResult;
import com.realapex.agent.tool.ToolRegistry;
import com.realapex.tool.schema.SchemaGenerator;
import com.voyage.agent.AgentTools;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * AI Agent 会话 Controller —— 通过 ai-agent-sdk 驱动 ReAct 循环。
 * <p>
 * 与 {@link AiChatController}（直接使用 ai-client-sdk 做基础对话）互补，
 * 本 Controller 使用 AgentRunner + AgentEventListener 提供带工具调用的 Agent 能力，
 * 并通过 SSE 命名事件将 ReAct 过程实时推送给前端。
 */
@Slf4j
@RestController
@RequestMapping("/api/agent")
public class AgentChatController {

    private static final String UPLOAD_DIR = System.getProperty("java.io.tmpdir") + "/ai_workbench_uploads/";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final AgentRunner agentRunner;
    private final SchemaGenerator schemaGenerator;

    private final AgentTools.SystemLogTool systemLogTool;
    private final AgentTools.HeavyMetricsTool heavyMetricsTool;
    private final AgentTools.SystemCommandTool systemCommandTool;
    private final AgentTools.LocalFileReaderTool localFileReaderTool;

    public AgentChatController(AgentRunner agentRunner,
                               SchemaGenerator schemaGenerator,
                               AgentTools.SystemLogTool systemLogTool,
                               AgentTools.HeavyMetricsTool heavyMetricsTool,
                               AgentTools.SystemCommandTool systemCommandTool,
                               AgentTools.LocalFileReaderTool localFileReaderTool) {
        this.agentRunner = agentRunner;
        this.schemaGenerator = schemaGenerator;
        this.systemLogTool = systemLogTool;
        this.heavyMetricsTool = heavyMetricsTool;
        this.systemCommandTool = systemCommandTool;
        this.localFileReaderTool = localFileReaderTool;

        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
        } catch (IOException e) {
            log.warn("无法创建上传目录: {}", UPLOAD_DIR, e);
        }
    }

    // ==================== 文件上传 ====================

    /**
     * 上传文件，供 Agent 通过 local_file_reader 工具读取分析。
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        String fileId = UUID.randomUUID() + "_" + file.getOriginalFilename();
        Path targetPath = Paths.get(UPLOAD_DIR, fileId);
        Files.copy(file.getInputStream(), targetPath);

        log.info("文件上传成功: {} -> {}", file.getOriginalFilename(), targetPath);

        return ResponseEntity.ok(Map.of(
                "fileId", fileId,
                "fileName", file.getOriginalFilename(),
                "filePath", targetPath.toAbsolutePath().toString()
        ));
    }

    // ==================== Agent SSE 流式对话 ====================

    /**
     * Agent 流式对话（SSE）。
     * <p>
     * 通过 AgentRunner 驱动 ReAct 循环，在虚拟线程中执行，
     * 将每个步骤的事件（step_start / tool_start / tool_end / text_chunk / complete）
     * 通过 SSE 命名事件实时推送给前端。
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody Map<String, String> body) {
        String prompt = body.getOrDefault("prompt", "");
        String filePath = body.getOrDefault("filePath", "");

        log.info("Agent chat 请求, prompt length: {}, filePath: {}",
                prompt.length(), filePath.isEmpty() ? "(无)" : filePath);

        SseEmitter emitter = new SseEmitter(180_000L);

        final String fullPrompt = prompt
                + (filePath != null && !filePath.isBlank()
                        ? "\n【系统提示】用户上传了文件，路径: " + filePath
                                + "，请调用 local_file_reader 工具读取文件内容并分析。"
                        : "");

        String systemPrompt = """
                你是一个资深技术运维与代码分析助手。
                你可以使用以下工具完成任务：
                - query_system_log：查询系统日志
                - get_heavy_metrics：获取性能指标
                - execute_system_command：执行系统命令（高危命令会被安全沙箱拦截）
                - local_file_reader：读取分析本地文件

                规则：
                1. 如果用户要求执行敏感命令，必须优先调用系统命令工具以触发安全机制校验
                2. 遇到需要分析文件时，调用 local_file_reader 工具
                3. 请逐步思考并解决问题，每次调用工具后分析结果再决定下一步
                """;

        ToolRegistry toolRegistry = new ToolRegistry(schemaGenerator);
        toolRegistry.register(systemLogTool);
        toolRegistry.register(heavyMetricsTool);
        toolRegistry.register(systemCommandTool);
        toolRegistry.register(localFileReaderTool);

        Thread.startVirtualThread(() -> {
            try {
                AgentResult result = agentRunner.run(AgentRequest.builder()
                        .systemPrompt(systemPrompt)
                        .userPrompt(fullPrompt)
                        .tools(toolRegistry.getAll())
                        .maxSteps(8)
                        .listener(new AgentEventListener() {
                            @Override
                            public void onStepStart(int step) {
                                sendSseEvent(emitter, "step_start",
                                        Map.of("step", step));
                            }

                            @Override
                            public void onChunk(String textChunk) {
                                sendSseData(emitter, "text_chunk", textChunk);
                            }

                            @Override
                            public void onToolStart(String toolName, String toolInputJson) {
                                sendSseEvent(emitter, "tool_start",
                                        Map.of("name", toolName, "args", toolInputJson));
                            }

                            @Override
                            public void onToolEnd(String toolName, Object toolOutput) {
                                String output = toolOutput != null ? toolOutput.toString() : "(无输出)";
                                sendSseEvent(emitter, "tool_end", Map.of(
                                        "name", toolName,
                                        "output", output,
                                        "length", output.length(),
                                        "truncated", output.length() > 800
                                ));
                            }

                            @Override
                            public void onStepFinish(AgentStepResult stepResult) {
                                log.debug("Step {} 完成, 耗时: {}ms, tokens: {}",
                                        stepResult.getStepNumber(),
                                        stepResult.getDurationMs(),
                                        stepResult.getUsage() != null ? stepResult.getUsage().getTotalTokens() : 0);
                            }

                            @Override
                            public void onComplete(AgentResult result) {
                                if (result.getFinalText() != null && !result.getFinalText().isEmpty()) {
                                    sendSseData(emitter, "text_chunk", result.getFinalText());
                                }
                                sendSseEvent(emitter, "complete", Map.of(
                                        "totalSteps", result.getTotalSteps(),
                                        "totalTokens", result.getTotalTokens(),
                                        "totalDurationMs", result.getTotalDurationMs(),
                                        "finalText", result.getFinalText()
                                ));
                                emitter.complete();
                                log.info("Agent 对话完成, steps: {}, tokens: {}",
                                        result.getTotalSteps(), result.getTotalTokens());
                            }
                        })
                        .build());

                log.debug("Agent 执行完成, finalText length: {}",
                        result.getFinalText() != null ? result.getFinalText().length() : 0);

            } catch (Exception e) {
                log.error("Agent 执行异常: {}", e.getMessage(), e);
                sendSseData(emitter, "error", e.getMessage());
                // 用 complete() 而非 completeWithError()，
                // 确保 error 事件已刷新到客户端再关闭连接
                emitter.complete();
            }
        });

        return emitter;
    }

    // ==================== SSE 发送工具方法 ====================

    private void sendSseEvent(SseEmitter emitter, String eventName, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (IOException e) {
            log.debug("SSE 发送失败 (客户端可能已断开): {}", e.getMessage());
        }
    }

    private void sendSseData(SseEmitter emitter, String eventName, String text) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(text));
        } catch (IOException e) {
            log.debug("SSE 发送失败 (客户端可能已断开): {}", e.getMessage());
        }
    }
}

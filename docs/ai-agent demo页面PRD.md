为了验证 **`ai-agent-sdk`**（状态机/ReAct 驱动/事件流）和 **`ai-tool-sdk`**（@Tool 契约/Schema 生成/安全沙箱）的核心功能，Demo 页面需要具备 **“交互性”** 与 **“可视化的排查链路”**。

我们需要验证的**关键指标**包括：

1. **多步工具调用（ReAct 循环）**：Agent 是否能根据问题自主决定多次调用工具。
2. **安全沙箱拦截（SafeGuard）**：高危指令（如 `rm -rf` 或 `DROP TABLE`）是否被 `ai-tool-sdk` 自动拦截。
3. **思考过程与工具事件流（CoT Stream）**：前端能否实时打字机展示 Thought、Tool Inputs 和 Tool Outputs。
4. **长文本截断与 Context 剪裁**：工具返回大文本时是否被截断，避免 Token 爆表。

以下为你提供一套 **全栈验证 Demo 架构与代码实现**（后端 Spring Boot 接口 + 前端 Web 交互界面）。

---

## 一、 后端 Demo 代码实现 (Spring Boot)

### 1. 定义测试工具（验证 `ai-tool-sdk`）

在后端定义 3 个代表性的测试工具，涵盖**常规工具、大文本工具、高危被拦截工具**：

```java
package com.yourcompany.demo.tools;

import com.yourcompany.ai.tool.annotation.Tool;
import com.yourcompany.ai.tool.contract.AgentTool;
import org.springframework.stereotype.Component;

public class DemoTools {

    // 1. 常规查询工具：查询系统日志
    @Component
    @Tool(name = "query_system_log", description = "查询指定模块的系统运行日志，返回日志列表")
    public static class SystemLogTool implements AgentTool<SystemLogTool.Req, String> {
        public record Req(String module, int lines) {}

        @Override
        public String execute(Req req) {
            return String.format("""
                [2026-08-10 16:20:01] INFO [%s] - Server started on port 8080.
                [2026-08-10 16:20:05] WARN [%s] - Slow query detected in database pool! Cost: 3450ms.
                [2026-08-10 16:20:10] ERROR [%s] - NullPointerException in OrderService.java:42
                """, req.module(), req.module(), req.module());
        }
    }

    // 2. 模拟大文本工具：触发 ai-tool-sdk 的 ToolResultTruncator 截断机制
    @Component
    @Tool(name = "get_heavy_metrics", description = "获取系统全量性能指标数据（数据量极大）")
    public static class HeavyMetricsTool implements AgentTool<HeavyMetricsTool.Req, String> {
        public record Req(String nodeIp) {}

        @Override
        public String execute(Req req) {
            StringBuilder sb = new StringBuilder("=== Metrics Start ===\n");
            for (int i = 0; i < 2000; i++) {
                sb.append("metric_cpu_usage_core_").append(i).append("=").append(Math.random()).append("\n");
            }
            sb.append("=== Metrics End ===");
            return sb.toString(); // 返回几万字的超长文本，测试截断
        }
    }

    // 3. 高危工具：测试 SafeGuard 拦截机制
    @Component
    @Tool(name = "execute_system_command", description = "在服务器上执行指定的 Shell 系统命令")
    public static class SystemCommandTool implements AgentTool<SystemCommandTool.Req, String> {
        public record Req(String command) {}

        @Override
        public String execute(Req req) {
            // 真实生产中，ai-tool-sdk 的 SafeGuard 拦截器会在进入 execute 之前生效
            if (req.command().contains("rm") || req.command().contains("drop")) {
                throw new SecurityException("[SafeGuard] 高危命令将被安全沙箱强行拦截: " + req.command());
            }
            return "Command executed successfully: " + req.command();
        }
    }
}

```

---

### 2. Controller 接口（验证 `ai-agent-sdk` SSE 事件流）

提供一个 SSE (Server-Sent Events) 接口，将 Agent 的 ReAct 过程（Thinking、Tool Calling、Tool Result、Final Answer）实时推送给前端。

```java
package com.yourcompany.demo.controller;

import com.yourcompany.ai.agent.runner.AgentRunner;
import com.yourcompany.ai.agent.event.AgentEventListener;
import com.yourcompany.ai.tool.registry.ToolRegistry;
import com.yourcompany.demo.tools.DemoTools;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*") // 允许前端 Demo 跨域
public class AgentDemoController {

    private final AgentRunner agentRunner;
    private final DemoTools.SystemLogTool systemLogTool;
    private final DemoTools.HeavyMetricsTool heavyMetricsTool;
    private final DemoTools.SystemCommandTool systemCommandTool;

    public AgentDemoController(AgentRunner agentRunner,
                               DemoTools.SystemLogTool systemLogTool,
                               DemoTools.HeavyMetricsTool heavyMetricsTool,
                               DemoTools.SystemCommandTool systemCommandTool) {
        this.agentRunner = agentRunner;
        this.systemLogTool = systemLogTool;
        this.heavyMetricsTool = heavyMetricsTool;
        this.systemCommandTool = systemCommandTool;
    }

    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestParam("prompt") String prompt) {
        SseEmitter emitter = new SseEmitter(180_000L); // 3 分钟超时

        // 1. 动态构造 ToolRegistry (来自于 ai-tool-sdk)
        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(systemLogTool);
        toolRegistry.register(heavyMetricsTool);
        toolRegistry.register(systemCommandTool);

        // 2. 虚拟线程异步驱动 Agent (来自于 ai-agent-sdk)
        Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            try {
                String systemPrompt = """
                    你是一个资深运维排查 AI 助手。
                    你有权限调用日志查询、指标拉取和系统命令工具。
                    如果用户要求执行敏感命令，必须优先调用系统命令工具（以触发安全机制校验）。
                    请逐步思考并解决问题。
                    """;

                agentRunner.streamRun(
                    systemPrompt,
                    prompt,
                    toolRegistry,
                    new AgentEventListener() {
                        @Override
                        public void onStepStart(int step) {
                            sendSse(emitter, "step_start", "--- 第 " + step + " 轮 ReAct 思考 ---");
                        }

                        @Override
                        public void onChunk(String textChunk) {
                            sendSse(emitter, "text_chunk", textChunk);
                        }

                        @Override
                        public void onToolStart(String toolName, String toolInputJson) {
                            sendSse(emitter, "tool_start", String.format("🔧 [调用工具] %s | 参数: %s", toolName, toolInputJson));
                        }

                        @Override
                        public void onToolEnd(String toolName, String toolOutput) {
                            sendSse(emitter, "tool_end", String.format("✅ [工具返回] %s | 结果: %s", toolName, toolOutput));
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            sendSse(emitter, "error", "❌ 发生错误: " + throwable.getMessage());
                        }

                        @Override
                        public void onComplete() {
                            sendSse(emitter, "complete", "[DONE]");
                            emitter.complete();
                        }
                    }
                );
            } catch (Exception e) {
                sendSse(emitter, "error", "系统异常: " + e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void sendSse(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            // client disconnected
        }
    }
}

```

---

## 二、 前端 Demo HTML 页面（单文件开箱即用）

创建一个简单的 `index.html`，无需安装复杂的 Node.js 环境，直接浏览器双击打开即可使用原生 `EventSource` 进行验证。

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>AI Workbench 铁三角功能验证 Demo</title>
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif; margin: 20px; background-color: #f5f7fa; }
        .container { max-width: 900px; margin: 0 auto; background: #fff; padding: 20px; border-radius: 8px; box-shadow: 0 2px 12px rgba(0,0,0,0.1); }
        h2 { border-bottom: 2px solid #409eff; padding-bottom: 10px; color: #303133; }
        .preset-btns { margin-bottom: 15px; }
        .preset-btn { background: #e6f7ff; border: 1px solid #91d5ff; color: #1890ff; padding: 6px 12px; border-radius: 4px; cursor: pointer; margin-right: 8px; margin-bottom: 5px; }
        .preset-btn:hover { background: #bae7ff; }
        .input-group { display: flex; gap: 10px; margin-bottom: 20px; }
        input[type="text"] { flex: 1; padding: 10px; border: 1px solid #dcdfe6; border-radius: 4px; font-size: 14px; }
        button.send-btn { padding: 10px 20px; background: #409eff; color: #fff; border: none; border-radius: 4px; cursor: pointer; font-weight: bold; }
        button.send-btn:hover { background: #66b1ff; }
        #console { background: #1e1e1e; color: #d4d4d4; padding: 15px; border-radius: 6px; font-family: "Courier New", Courier, monospace; min-height: 400px; max-height: 600px; overflow-y: auto; white-space: pre-wrap; word-break: break-all; font-size: 13px; line-height: 1.5; }
        .event-step { color: #e6a23c; font-weight: bold; margin-top: 10px; display: block; }
        .event-text { color: #67c23a; }
        .event-tool-start { color: #409eff; background: rgba(64,158,255,0.1); padding: 2px 4px; border-radius: 2px; }
        .event-tool-end { color: #909399; font-style: italic; }
        .event-error { color: #f56c6c; font-weight: bold; }
    </style>
</head>
<body>

<div class="container">
    <h2>🧪 AI Workbench 基座 (ai-agent-sdk & ai-tool-sdk) 验证平台</h2>

    <!-- 预置验证场景按钮 -->
    <div class="preset-btns">
        <strong>快捷验证预置词：</strong><br>
        <button class="preset-btn" onclick="setPrompt('帮我检查一下 order 模块的系统日志，看看有什么报错？')">1. 验证多步 Tool 调用</button>
        <button class="preset-btn" onclick="setPrompt('请拉取服务器节点 192.168.1.100 的全量性能指标数据')">2. 验证长文本截断 (Truncator)</button>
        <button class="preset-btn" onclick="setPrompt('请帮我在服务器上执行命令：rm -rf /tmp/logs')">3. 验证安全沙箱拦截 (SafeGuard)</button>
    </div>

    <div class="input-group">
        <input type="text" id="promptInput" placeholder="输入测试指令..." />
        <button class="send-btn" onclick="startStream()">发送指令</button>
    </div>

    <h3>实时事件流日志 (ReAct State Machine Tracking)：</h3>
    <div id="console">等待发送指令...</div>
</div>

<script>
    function setPrompt(text) {
        document.getElementById('promptInput').value = text;
    }

    function startStream() {
        const prompt = document.getElementById('promptInput').value;
        if (!prompt) return;

        const consoleDiv = document.getElementById('console');
        consoleDiv.innerHTML = `[System] 开始建立 SSE 连接，发送 Prompt: "${prompt}"\n`;

        // 建立 SSE 连接
        const url = `http://localhost:8080/api/agent/chat?prompt=${encodeURIComponent(prompt)}`;
        const eventSource = new EventSource(url);

        eventSource.addEventListener('step_start', (e) => {
            consoleDiv.innerHTML += `<span class="event-step">\n${e.data}</span>\n`;
            scrollToBottom();
        });

        eventSource.addEventListener('text_chunk', (e) => {
            consoleDiv.innerHTML += `<span class="event-text">${e.data}</span>`;
            scrollToBottom();
        });

        eventSource.addEventListener('tool_start', (e) => {
            consoleDiv.innerHTML += `\n<span class="event-tool-start">${e.data}</span>\n`;
            scrollToBottom();
        });

        eventSource.addEventListener('tool_end', (e) => {
            consoleDiv.innerHTML += `<span class="event-tool-end">${e.data}</span>\n`;
            scrollToBottom();
        });

        eventSource.addEventListener('error', (e) => {
            consoleDiv.innerHTML += `\n<span class="event-error">${e.data}</span>\n`;
            scrollToBottom();
            eventSource.close();
        });

        eventSource.addEventListener('complete', (e) => {
            consoleDiv.innerHTML += `\n\n[System] ✅ 流程完毕，会话已闭合。`;
            scrollToBottom();
            eventSource.close();
        });

        eventSource.onerror = (err) => {
            console.log("SSE Error/Closed", err);
            eventSource.close();
        };
    }

    function scrollToBottom() {
        const consoleDiv = document.getElementById('console');
        consoleDiv.scrollTop = consoleDiv.scrollHeight;
    }
</script>

</body>
</html>

```

---

## 三、 功能验证对照指南

运行后，点击预置的 3 个按钮依次测试，可以在终端/控制台上清晰校验你的 SDK 底座能力：

| 测试用例 | 观察重点 (期望的表现) | 验证的 SDK 核心能力 |
| --- | --- | --- |
| **测试 1：日志排查** | 看到 Agent 自动打印 `tool_start` -> 调用 `query_system_log` -> 拿到日志 -> 分析日志并给出 NullPointerException 结论。 | **`ai-agent-sdk`** 的 ReAct `while` 循环调度 + 多步工具闭环能力。 |
| **测试 2：大文本返回** | 工具返回 2000 行文本，控制台中看到的工具返回内容被自动处理为 `=== Metrics Start === ... [Truncated 18000 chars] ... === Metrics End ===`。 | **`ai-tool-sdk`** 的 `ToolResultTruncator` (大文本截断) 能力，保护 Prompt 不暴掉。 |
| **测试 3：高危命令拦截** | 点击 `rm -rf`，看到控制台在 `tool_end` 或 `error` 中明确打印出 `[SafeGuard] 高危命令将被安全沙箱强行拦截`，Agent 捕获异常后回复用户“无法执行高危命令”。 | **`ai-tool-sdk`** 的安全沙箱 (SafeGuard) 拦截防护能力。 |
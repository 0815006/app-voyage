
为了验证 **`ai-agent-sdk`** 的文件交互能力以及 **`ai-tool-sdk`** 的文件解析/截断防护机制，我们可以将前面的 Demo 升级为一个标准的 **AI 聊天对话框界面**。

在升级后的架构中：

1. **文件上传**：点击 `+` 号上传本地文件，后端将文件临时保存，并生成系统内的 `fileId` 或 `filePath`。
2. **多模态/文件 Tool 联动**：前端发送带有文件引用的消息，Agent 识别到文件后，自动调用 `FileReaderTool` 或 `FileParserTool` 进行读取与分析。
3. **长文本防护**：若上传的文件超大（如几兆的日志文件或 CSV），`ai-tool-sdk` 的 `ToolResultTruncator` 会自动截断中间部分，只透出关键头尾信息给大模型，防止 Prompt 爆表。

下面为你提供一套包含 **“上传文件 + 聊天对话框 + 实时工具流（Thinking / Tool Events）”** 的全栈验证代码。

---

## 一、 后端 Demo 代码实现 (Spring Boot)

### 1. 注册文件读取工具与文件存储接口

新增文件读取 Tool（依赖 `ai-tool-sdk` 契约）以及文件上传 API：

```java
package com.yourcompany.demo.controller;

import com.yourcompany.ai.agent.event.AgentEventListener;
import com.yourcompany.ai.agent.runner.AgentRunner;
import com.yourcompany.ai.tool.annotation.Tool;
import com.yourcompany.ai.tool.contract.AgentTool;
import com.yourcompany.ai.tool.registry.ToolRegistry;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
public class AgentFileDemoController {

    private static final String UPLOAD_DIR = System.getProperty("java.io.tmpdir") + "/ai_workbench_uploads/";

    private final AgentRunner agentRunner;
    private final LocalFileReaderTool fileReaderTool;

    public AgentFileDemoController(AgentRunner agentRunner, LocalFileReaderTool fileReaderTool) {
        this.agentRunner = agentRunner;
        this.fileReaderTool = fileReaderTool;
        // 创建临时上传目录
        new File(UPLOAD_DIR).mkdirs();
    }

    // 1. 文件上传接口
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) throws IOException {
        String fileId = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path targetPath = Paths.get(UPLOAD_DIR, fileId);
        Files.copy(file.getInputStream(), targetPath);

        return ResponseEntity.ok(Map.of(
            "fileId", fileId,
            "fileName", file.getOriginalFilename(),
            "filePath", targetPath.toAbsolutePath().toString()
        ));
    }

    // 2. 对话与 Agent 驱动接口 (支持带文件上下文的 Prompt)
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestParam("prompt") String prompt,
                           @RequestParam(value = "filePath", required = false) String filePath) {
        SseEmitter emitter = new SseEmitter(180_000L);

        ToolRegistry toolRegistry = new ToolRegistry();
        toolRegistry.register(fileReaderTool);

        Executors.newVirtualThreadPerTaskExecutor().submit(() -> {
            try {
                String fullPrompt = prompt;
                if (filePath != null && !filePath.isBlank()) {
                    fullPrompt += "\n【附加系统文件提示】用户上传了文件，文件路径为: " + filePath + "，请根据需求调用 local_file_reader 工具读取并分析。";
                }

                String systemPrompt = """
                    你是一个专业的代码与文档分析助手。
                    当用户提出针对特定文件的分析需求时，请调用 local_file_reader 工具读取文件内容，然后做出详细回答。
                    """;

                agentRunner.streamRun(
                    systemPrompt,
                    fullPrompt,
                    toolRegistry,
                    new AgentEventListener() {
                        @Override
                        public void onStepStart(int step) {
                            sendSse(emitter, "step_start", "--- ReAct Step " + step + " ---");
                        }

                        @Override
                        public void onChunk(String textChunk) {
                            sendSse(emitter, "text_chunk", textChunk);
                        }

                        @Override
                        public void onToolStart(String toolName, String toolInputJson) {
                            sendSse(emitter, "tool_start", String.format("🔧 [Tool Call] %s | Args: %s", toolName, toolInputJson));
                        }

                        @Override
                        public void onToolEnd(String toolName, String toolOutput) {
                            // 此处的 toolOutput 会自动触发 ai-tool-sdk 的 ToolResultTruncator 截断机制
                            sendSse(emitter, "tool_end", String.format("✅ [Tool Output] %s | Length: %d chars", toolName, toolOutput.length()));
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            sendSse(emitter, "error", "❌ Error: " + throwable.getMessage());
                        }

                        @Override
                        public void onComplete() {
                            sendSse(emitter, "complete", "[DONE]");
                            emitter.complete();
                        }
                    }
                );
            } catch (Exception e) {
                sendSse(emitter, "error", e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void sendSse(SseEmitter emitter, String eventName, String data) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException ignored) {}
    }

    // 3. 本地文件读取工具 (@Tool 实现)
    @Component
    @Tool(name = "local_file_reader", description = "读取指定绝对路径的文件内容（支持 TXT, LOG, JSON, Java, MD 等文件）")
    public static class LocalFileReaderTool implements AgentTool<LocalFileReaderTool.Req, String> {
        public record Req(String filePath) {}

        @Override
        public String execute(Req req) {
            try {
                Path path = Paths.get(req.filePath());
                if (!Files.exists(path)) {
                    return "Error: 文件不存在: " + req.filePath();
                }
                // 返回文件完整内容（由 ai-tool-sdk 负责拦截与截断）
                return Files.readString(path);
            } catch (Exception e) {
                return "Error reading file: " + e.getMessage();
            }
        }
    }
}

```

---

## 二、 前端完整 Demo 界面 (单文件 HTML 对话框 UI)

前端模拟常见的 AI 聊天界面，提供底栏 `+` 号按钮实现文件上传预览，并在对话卡片中打印 Agent 的思考及工具调用过程。

保存为 `chat_demo.html`，浏览器打开即可测试：

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>AI Workbench - 文件分析 Demo</title>
    <style>
        * { box-sizing: border-box; }
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background-color: #f0f2f5; margin: 0; padding: 20px; display: flex; justify-content: center; }
        .chat-container { width: 800px; height: 90vh; background: #fff; border-radius: 12px; box-shadow: 0 4px 20px rgba(0,0,0,0.08); display: flex; flex-direction: column; overflow: hidden; }
        .chat-header { padding: 16px 20px; background: #fff; border-bottom: 1px solid #e8e8e8; font-size: 18px; font-weight: bold; color: #1f1f1f; display: flex; justify-content: space-between; align-items: center; }
        .chat-messages { flex: 1; padding: 20px; overflow-y: auto; background: #fafafa; display: flex; flex-direction: column; gap: 16px; }
      
        .message { display: flex; flex-direction: column; max-width: 85%; }
        .message.user { align-self: flex-end; }
        .message.assistant { align-self: flex-start; }
      
        .bubble { padding: 12px 16px; border-radius: 8px; font-size: 14px; line-height: 1.6; word-break: break-word; }
        .message.user .bubble { background: #1677ff; color: #fff; border-bottom-right-radius: 2px; }
        .message.assistant .bubble { background: #fff; border: 1px solid #e8e8e8; color: #1f1f1f; border-bottom-left-radius: 2px; }

        .file-tag { display: inline-flex; align-items: center; gap: 6px; background: rgba(255,255,255,0.2); padding: 4px 8px; border-radius: 4px; font-size: 12px; margin-bottom: 6px; }
        .assistant .file-tag { background: #f0f0f0; color: #595959; }

        /* Agent 思考与工具日志事件 */
        .event-log { font-family: monospace; font-size: 12px; background: #262626; color: #d9d9d9; padding: 10px; border-radius: 6px; margin-bottom: 8px; white-space: pre-wrap; max-height: 200px; overflow-y: auto; }
        .event-log .tool { color: #1677ff; }
        .event-log .step { color: #faad14; font-weight: bold; }

        .chat-input-area { padding: 16px; background: #fff; border-top: 1px solid #e8e8e8; display: flex; flex-direction: column; gap: 8px; }
        .file-preview { display: none; align-items: center; gap: 8px; background: #e6f4ff; color: #1677ff; padding: 6px 12px; border-radius: 6px; font-size: 13px; width: fit-content; }
        .file-preview .remove-btn { cursor: pointer; font-weight: bold; margin-left: 6px; }

        .input-toolbar { display: flex; gap: 10px; align-items: center; }
        .upload-btn { width: 36px; height: 36px; border-radius: 50%; border: 1px solid #d9d9d9; display: flex; align-items: center; justify-content: center; cursor: pointer; font-size: 20px; color: #595959; background: #fff; transition: all 0.2s; }
        .upload-btn:hover { border-color: #1677ff; color: #1677ff; background: #e6f4ff; }
      
        textarea { flex: 1; height: 40px; border: 1px solid #d9d9d9; border-radius: 6px; padding: 8px 12px; font-size: 14px; resize: none; outline: none; }
        textarea:focus { border-color: #1677ff; }
        .send-btn { padding: 0 20px; height: 40px; background: #1677ff; color: #fff; border: none; border-radius: 6px; cursor: pointer; font-weight: bold; }
        .send-btn:hover { background: #4096ff; }
        #fileInput { display: none; }
    </style>
</head>
<body>

<div class="chat-container">
    <div class="chat-header">
        <span>🤖 AI Workbench Agent 文件分析测试</span>
        <span style="font-size: 12px; color: #8c8c8c; font-weight: normal;">底座：ai-agent-sdk + ai-tool-sdk</span>
    </div>

    <div class="chat-messages" id="messageList">
        <div class="message assistant">
            <div class="bubble">你好！点击左下角的 <b>+</b> 号上传本地日志或代码文件，我可以通过 <code>local_file_reader</code> 工具为您提取并分析内容。</div>
        </div>
    </div>

    <div class="chat-input-area">
        <!-- 上传文件后的预览条 -->
        <div class="file-preview" id="filePreview">
            <span>📄 <span id="fileNameDisplay">filename.txt</span></span>
            <span class="remove-btn" onclick="clearFile()">✕</span>
        </div>

        <div class="input-toolbar">
            <button class="upload-btn" onclick="document.getElementById('fileInput').click()" title="上传文件">+</button>
            <input type="file" id="fileInput" onchange="handleFileUpload(this)" />
            <textarea id="promptInput" placeholder="输入关于文件的分析指令，例如：'分析这个文件里的错误日志'..." onkeydown="handleKeyDown(event)"></textarea>
            <button class="send-btn" onclick="sendMessage()">发送</button>
        </div>
    </div>
</div>

<script>
    let currentUploadedFilePath = "";
    let currentUploadedFileName = "";

    // 上传文件逻辑
    async function handleFileUpload(input) {
        if (!input.files || input.files.length === 0) return;
        const file = input.files[0];

        const formData = new FormData();
        formData.append("file", file);

        try {
            const res = await fetch("http://localhost:8080/api/agent/upload", {
                method: "POST",
                body: formData
            });
            const data = await res.json();

            currentUploadedFilePath = data.filePath;
            currentUploadedFileName = data.fileName;

            document.getElementById("fileNameDisplay").innerText = data.fileName;
            document.getElementById("filePreview").style.display = "flex";
        } catch (e) {
            alert("文件上传失败: " + e.message);
        }
    }

    function clearFile() {
        currentUploadedFilePath = "";
        currentUploadedFileName = "";
        document.getElementById("fileInput").value = "";
        document.getElementById("filePreview").style.display = "none";
    }

    function handleKeyDown(e) {
        if (e.key === "Enter" && !e.shiftKey) {
            e.preventDefault();
            sendMessage();
        }
    }

    async function sendMessage() {
        const inputEl = document.getElementById("promptInput");
        const prompt = inputEl.value.trim();
        if (!prompt && !currentUploadedFilePath) return;

        const messageList = document.getElementById("messageList");

        // 1. 渲染 User 消息
        let userBubbleHtml = prompt;
        if (currentUploadedFileName) {
            userBubbleHtml = `<div class="file-tag">📄 ${currentUploadedFileName}</div><br>` + userBubbleHtml;
        }

        const userMsgDiv = document.createElement("div");
        userMsgDiv.className = "message user";
        userMsgDiv.innerHTML = `<div class="bubble">${userBubbleHtml}</div>`;
        messageList.appendChild(userMsgDiv);

        // 清空输入框
        inputEl.value = "";
        const filePathToSend = currentUploadedFilePath;
        clearFile(); // 清空上传暂存区

        // 2. 创建 Assistant 占位消息框
        const assistantMsgDiv = document.createElement("div");
        assistantMsgDiv.className = "message assistant";
      
        const logBox = document.createElement("div");
        logBox.className = "event-log";
        logBox.style.display = "none"; // 默认隐藏，有工具调用时显示

        const bubbleBox = document.createElement("div");
        bubbleBox.className = "bubble";
        bubbleBox.innerText = "思考中...";

        assistantMsgDiv.appendChild(logBox);
        assistantMsgDiv.appendChild(bubbleBox);
        messageList.appendChild(assistantMsgDiv);
        scrollToBottom();

        // 3. 建立 SSE 连接调用后端 Agent
        let url = `http://localhost:8080/api/agent/chat?prompt=${encodeURIComponent(prompt)}`;
        if (filePathToSend) {
            url += `&filePath=${encodeURIComponent(filePathToSend)}`;
        }

        const eventSource = new EventSource(url);
        let hasStartedChunk = false;

        eventSource.addEventListener("step_start", (e) => {
            logBox.style.display = "block";
            logBox.innerHTML += `<span class="step">\n${e.data}</span>\n`;
            scrollToBottom();
        });

        eventSource.addEventListener("tool_start", (e) => {
            logBox.style.display = "block";
            logBox.innerHTML += `<span class="tool">${e.data}</span>\n`;
            scrollToBottom();
        });

        eventSource.addEventListener("tool_end", (e) => {
            logBox.innerHTML += `<span>${e.data}</span>\n`;
            scrollToBottom();
        });

        eventSource.addEventListener("text_chunk", (e) => {
            if (!hasStartedChunk) {
                bubbleBox.innerText = ""; // 首次接收打字机输出时清空“思考中...”
                hasStartedChunk = true;
            }
            bubbleBox.innerText += e.data;
            scrollToBottom();
        });

        eventSource.addEventListener("error", (e) => {
            bubbleBox.innerText += "\n[Error] " + e.data;
            eventSource.close();
        });

        eventSource.addEventListener("complete", () => {
            eventSource.close();
        });
    }

    function scrollToBottom() {
        const messageList = document.getElementById("messageList");
        messageList.scrollTop = messageList.scrollHeight;
    }
</script>

</body>
</html>

```

---

## 三、 验证测试步骤

1. **准备测试文件**：在本地创建一个名为 `test_app.log` 的日志文件（或者拷贝一段 Java 代码文件），故意填入一些异常日志或代码段。
2. **操作上传**：

* 打开 HTML 页面，点击左下角 **`+`** 号，选择本地的 `test_app.log` 文件。
* 上传成功后对话框底部会出现文件标签（如 `📄 test_app.log`）。

3. **输入分析指令**：

* 在输入框中输入：“*请分析一下这个文件，帮我找出来里面发生了什么异常，以及发生在第几行？*”，然后点击**发送**。

4. **观察 Agent 执行流**：

* **事件流黑盒 (logBox)**：会展示 Agent 的 ReAct 过程，如 `Tool Call: local_file_reader` 并把上传的临时绝对路径传给 Tool。
* **截断机制**：如果文件较大，控制台中 `tool_end` 会显示长度限制，验证了 `ai-tool-sdk` 的截断保护。
* **最终回复**：AI 读完文件后，打字机实时输出精准的日志/代码分析结论。

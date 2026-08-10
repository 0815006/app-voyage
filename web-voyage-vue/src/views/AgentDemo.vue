<template>
  <div class="agent-demo">
    <!-- ====== 标题栏 ====== -->
    <div class="demo-header">
      <div class="header-left">
        <h2 class="header-title">🧪 AI Workbench Agent 验证平台</h2>
        <span class="header-sub">底座：ai-agent-sdk + ai-tool-sdk</span>
      </div>
      <el-button size="small" text @click="clearAll">清空对话</el-button>
    </div>

    <!-- ====== 预设场景按钮 ====== -->
    <div class="preset-bar">
      <span class="preset-label">快捷验证：</span>
      <el-button
        v-for="preset in presets"
        :key="preset.label"
        size="small"
        :disabled="loading"
        class="preset-btn"
        @click="sendPreset(preset)"
      >
        {{ preset.label }}
      </el-button>
    </div>

    <!-- ====== 消息区域 ====== -->
    <div ref="msgContainer" class="chat-area">
      <!-- 空状态 -->
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">🤖</div>
        <div class="empty-title">Agent 功能验证平台</div>
        <div class="empty-desc">
          点击上方快捷按钮，或输入自定义指令，验证 AI Workbench SDK 的核心能力
        </div>
        <div class="empty-features">
          <span>ReAct 多步调用</span>
          <span>安全沙箱拦截</span>
          <span>长文本截断</span>
          <span>文件分析</span>
        </div>
      </div>

      <!-- 消息列表 -->
      <div
        v-for="(msg, i) in messages"
        :key="i"
        class="chat-msg"
        :class="msg.role"
      >
        <!-- 头像 -->
        <div class="msg-avatar">{{ msg.role === 'user' ? '🧑' : '🤖' }}</div>

        <div class="msg-body">
          <div class="msg-role">{{ msg.role === 'user' ? '你' : 'Agent' }}</div>

          <!-- 文件标签（用户消息） -->
          <el-tag
            v-if="msg.fileName"
            size="small"
            closable
            class="file-tag"
            @close="msg.fileName = ''"
          >
            📄 {{ msg.fileName }}
          </el-tag>

          <!-- Assistant: 事件日志面板 -->
          <div
            v-if="msg.role === 'assistant' && msg.events.length > 0"
            class="event-panel"
            :class="{ collapsed: msg.logCollapsed }"
          >
            <div class="event-header" @click="msg.logCollapsed = !msg.logCollapsed">
              <span>📋 ReAct 执行日志 ({{ msg.events.length }} 条)</span>
              <span class="event-toggle">{{ msg.logCollapsed ? '▶ 展开' : '▼ 折叠' }}</span>
            </div>
            <div v-show="!msg.logCollapsed" class="event-log">
              <div
                v-for="(ev, j) in msg.events"
                :key="j"
                class="event-line"
                :class="'ev-' + ev.type"
              >
                <template v-if="ev.type === 'step_start'">
                  <span class="ev-step">━━━ Step {{ ev.data.step }} ━━━</span>
                </template>
                <template v-else-if="ev.type === 'tool_start'">
                  <span class="ev-tool-label">🔧 调用工具</span>
                  <span class="ev-tool-name">{{ ev.data.name }}</span>
                  <span class="ev-tool-args">参数: {{ formatArgs(ev.data.args) }}</span>
                </template>
                <template v-else-if="ev.type === 'tool_end'">
                  <span class="ev-tool-label">✅ 工具返回</span>
                  <span class="ev-tool-name">{{ ev.data.name }}</span>
                  <span class="ev-tool-meta">
                    ({{ ev.data.length }} 字符{{ ev.data.truncated ? '，已截断' : '' }})
                  </span>
                  <pre class="ev-tool-output">{{ formatOutput(ev.data.output) }}</pre>
                </template>
                <template v-else-if="ev.type === 'error'">
                  <span class="ev-error">❌ {{ ev.data }}</span>
                </template>
              </div>
            </div>
          </div>

          <!-- 文本内容 -->
          <div
            class="msg-text"
            :class="{ streaming: msg.isStreaming }"
          >
            {{ msg.content || (msg.isStreaming ? '思考中...' : '') }}
            <span v-if="msg.isStreaming" class="cursor">|</span>
          </div>
        </div>
      </div>
    </div>

    <!-- ====== 输入区域 ====== -->
    <div class="input-area">
      <!-- 文件预览 -->
      <div v-if="uploadedFile" class="file-preview">
        <el-icon><Document /></el-icon>
        <span>{{ uploadedFile.name }}</span>
        <el-button text size="small" type="danger" @click="clearFile">移除</el-button>
      </div>

      <div class="input-row">
        <!-- 上传按钮 -->
        <el-button
          class="upload-btn"
          :disabled="loading"
          @click="triggerUpload"
        >
          <el-icon :size="18"><Plus /></el-icon>
        </el-button>
        <input
          ref="fileInput"
          type="file"
          style="display: none"
          @change="handleFileUpload"
        />

        <!-- 输入框 -->
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          :disabled="loading"
          placeholder="输入测试指令，Enter 发送，Shift+Enter 换行..."
          resize="none"
          @keydown.enter.exact="sendMessage"
        />

        <!-- 发送按钮 -->
        <el-button
          class="send-btn"
          type="primary"
          :disabled="(!inputText.trim() && !uploadedFile) || loading"
          :loading="loading"
          @click="sendMessage"
        >
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus, Document } from '@element-plus/icons-vue'

// ==================== 类型定义 ====================

interface AgentEvent {
  type: 'step_start' | 'tool_start' | 'tool_end' | 'error'
  data: any
}

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
  fileName?: string
  isStreaming: boolean
  events: AgentEvent[]
  logCollapsed: boolean
}

interface PresetItem {
  label: string
  prompt: string
}

// ==================== 状态 ====================

const presets: PresetItem[] = [
  { label: '1. 查询系统日志（ReAct 多步调用）', prompt: '帮我检查一下 order 模块的系统日志，看看有什么报错？' },
  { label: '2. 拉取性能指标（长文本截断）', prompt: '请拉取服务器节点 192.168.1.100 的全量性能指标数据，并分析 CPU 使用情况' },
  { label: '3. 高危命令拦截（安全沙箱）', prompt: '请帮我在服务器上执行命令：rm -rf /tmp/logs' },
]

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const loading = ref(false)
const uploadedFile = ref<{ name: string; path: string } | null>(null)
const fileInput = ref<HTMLInputElement>()
const msgContainer = ref<HTMLElement>()

// ==================== 文件上传 ====================

function triggerUpload() {
  fileInput.value?.click()
}

async function handleFileUpload(e: Event) {
  const input = e.target as HTMLInputElement
  if (!input.files || input.files.length === 0) return

  const file = input.files[0]
  const formData = new FormData()
  formData.append('file', file)

  try {
    const res = await fetch('/api/agent/upload', {
      method: 'POST',
      body: formData,
    })
    if (!res.ok) throw new Error(`上传失败 (${res.status})`)
    const data = await res.json()
    uploadedFile.value = { name: data.fileName, path: data.filePath }
    ElMessage.success(`文件已上传: ${data.fileName}`)
  } catch (err: any) {
    ElMessage.error('文件上传失败: ' + err.message)
  } finally {
    input.value = '' // 允许重复上传同一个文件
  }
}

function clearFile() {
  uploadedFile.value = null
}

// ==================== 预设 & 发送 ====================

function sendPreset(preset: PresetItem) {
  inputText.value = preset.prompt
  sendMessage()
}

async function sendMessage() {
  const prompt = inputText.value.trim()
  if (!prompt && !uploadedFile.value) return
  if (loading.value) return

  const filePath = uploadedFile.value?.path || ''
  const fileName = uploadedFile.value?.name || ''

  // 添加用户消息
  messages.value.push({
    role: 'user',
    content: prompt || '(分析上传文件)',
    fileName,
    isStreaming: false,
    events: [],
    logCollapsed: false,
  })

  inputText.value = ''
  clearFile()

  // 创建 Assistant 占位消息
  const assistantMsg: ChatMessage = {
    role: 'assistant',
    content: '',
    isStreaming: true,
    events: [],
    logCollapsed: false,
  }
  messages.value.push(assistantMsg)
  const msgIndex = messages.value.length - 1

  loading.value = true
  await nextTick()
  scrollToBottom()

  try {
    const response = await fetch('/api/agent/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ prompt: prompt || '请分析上传的文件', filePath }),
    })

    if (!response.ok) {
      const errText = await response.text()
      throw new Error(`API 错误 (${response.status}): ${errText}`)
    }

    const reader = response.body?.getReader()
    if (!reader) throw new Error('不支持流式读取')

    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })

      // 按 SSE 帧分割（\n\n 分隔）
      const frames = buffer.split('\n\n')
      buffer = frames.pop() || ''

      for (const frame of frames) {
        if (!frame.trim()) continue
        processSseFrame(frame, msgIndex)
        await nextTick()
        scrollToBottom()
      }
    }

    // 清除流式状态
    const msg = messages.value[msgIndex]
    if (msg) {
      msg.isStreaming = false
      if (!msg.content) msg.content = '(Agent 未返回文本内容)'
    }
  } catch (err: any) {
    const msg = messages.value[msgIndex]
    if (msg) {
      msg.isStreaming = false
      msg.events.push({ type: 'error', data: err.message })
      if (!msg.content) msg.content = '请求失败'
    }
    ElMessage.error(err.message || 'Agent 请求失败')
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

// ==================== SSE 帧解析 ====================

function processSseFrame(frame: string, msgIndex: number) {
  const msg = messages.value[msgIndex]
  if (!msg) return

  const lines = frame.split('\n')
  let eventType = ''
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith('event: ')) {
      eventType = line.slice(7).trim()
    } else if (line.startsWith('event:')) {
      eventType = line.slice(6).trim()
    } else if (line.startsWith('data: ')) {
      dataLines.push(line.slice(6))
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5))
    }
  }
  const data = dataLines.join('\n')

  if (!eventType || !data) return

  switch (eventType) {
    case 'text_chunk':
      // 打字机追加文本
      msg.content += data
      break

    case 'step_start':
      try {
        msg.events.push({ type: 'step_start', data: JSON.parse(data) })
      } catch { /* ignore parse error */ }
      break

    case 'tool_start':
      try {
        msg.events.push({ type: 'tool_start', data: JSON.parse(data) })
      } catch { /* ignore */ }
      break

    case 'tool_end':
      try {
        const toolEnd = JSON.parse(data)
        // 截断显示输出的前 500 字符
        if (toolEnd.output && toolEnd.output.length > 500) {
          toolEnd.output = toolEnd.output.slice(0, 500)
              + `\n... [截断显示，完整长度: ${toolEnd.length} 字符]`
        }
        msg.events.push({ type: 'tool_end', data: toolEnd })
      } catch { /* ignore */ }
      break

    case 'error':
      msg.events.push({ type: 'error', data })
      msg.content += '\n\n[错误] ' + data
      break

    case 'complete':
      try {
        const complete = JSON.parse(data)
        msg.events.push({
          type: 'step_start',
          data: { step: `完成 — ${complete.totalSteps} 步, ${complete.totalTokens} tokens` },
        })
        // 回退：如果 text_chunk 跨帧丢失，从 complete 事件提取 finalText
        if (!msg.content && complete.finalText) {
          msg.content = complete.finalText
        }
      } catch { /* ignore */ }
      break
  }
}

// ==================== 格式化辅助 ====================

function formatArgs(json: string): string {
  try {
    const obj = JSON.parse(json)
    // 截断过长的参数值
    const formatted = JSON.stringify(obj, null, 0)
    return formatted.length > 200 ? formatted.slice(0, 200) + '...' : formatted
  } catch {
    return json.length > 200 ? json.slice(0, 200) + '...' : json
  }
}

function formatOutput(output: string): string {
  if (!output) return '(空)'
  return output.length > 500 ? output.slice(0, 500) + '\n... [截断显示]' : output
}

// ==================== 通用 ====================

function clearAll() {
  messages.value = []
}

function scrollToBottom() {
  if (msgContainer.value) {
    msgContainer.value.scrollTop = msgContainer.value.scrollHeight
  }
}
</script>

<style scoped>
/* ========== 整体布局 ========== */
.agent-demo {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 160px);
  max-width: 960px;
  margin: 0 auto;
  padding: 16px;
  gap: 12px;
}

/* ========== 标题 ========== */
.demo-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 8px;
  border-bottom: 1px solid #ebeef5;
}
.header-left { display: flex; align-items: baseline; gap: 12px; }
.header-title { margin: 0; font-size: 18px; color: #303133; }
.header-sub { font-size: 12px; color: #909399; }

/* ========== 预设按钮 ========== */
.preset-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.preset-label { font-size: 13px; color: #606266; white-space: nowrap; }
.preset-btn { font-size: 12px; }

/* ========== 消息区域 ========== */
.chat-area {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 空状态 */
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
  text-align: center;
  gap: 8px;
}
.empty-icon { font-size: 48px; }
.empty-title { font-size: 18px; color: #606266; font-weight: 600; }
.empty-desc { font-size: 14px; max-width: 360px; line-height: 1.6; }
.empty-features {
  display: flex; gap: 8px; margin-top: 4px;
}
.empty-features span {
  background: #ecf5ff; color: #409eff;
  padding: 2px 10px; border-radius: 12px; font-size: 12px;
}

/* ========== 单条消息 ========== */
.chat-msg { display: flex; gap: 10px; }
.chat-msg.user { flex-direction: row-reverse; }
.msg-avatar {
  font-size: 28px; flex-shrink: 0;
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
}
.msg-body { max-width: 82%; display: flex; flex-direction: column; gap: 4px; }
.msg-role { font-size: 12px; color: #909399; }
.chat-msg.user .msg-role { text-align: right; }

.file-tag { width: fit-content; margin-bottom: 2px; }

/* 消息文本气泡 */
.msg-text {
  padding: 10px 14px; border-radius: 8px;
  font-size: 14px; line-height: 1.65;
  white-space: pre-wrap; word-break: break-word;
}
.chat-msg.user .msg-text {
  background: #409eff; color: #fff;
  border-bottom-right-radius: 2px;
}
.chat-msg.assistant .msg-text {
  background: #fff; border: 1px solid #e4e7ed;
  border-bottom-left-radius: 2px;
}
.msg-text.streaming .cursor {
  animation: blink 1s infinite;
}
@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* ========== 事件日志面板 ========== */
.event-panel {
  border: 1px solid #333;
  border-radius: 6px;
  overflow: hidden;
  margin-bottom: 4px;
}
.event-header {
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 10px;
  background: #2d2d2d; color: #ccc;
  font-size: 12px; cursor: pointer; user-select: none;
}
.event-header:hover { background: #3a3a3a; }
.event-toggle { color: #888; font-size: 11px; }
.event-log {
  background: #1e1e1e; color: #d4d4d4;
  font-family: 'Cascadia Code', 'Fira Code', 'JetBrains Mono', 'Courier New', monospace;
  font-size: 12px; line-height: 1.6;
  padding: 10px; max-height: 300px; overflow-y: auto;
  white-space: pre-wrap; word-break: break-all;
}
.event-line { margin-bottom: 6px; }
.ev-step { color: #e6a23c; font-weight: bold; display: block; margin-top: 4px; }
.ev-tool-label { color: #409eff; }
.ev-tool-name { color: #67c23a; font-weight: bold; margin: 0 4px; }
.ev-tool-args { color: #909399; font-size: 11px; }
.ev-tool-meta { color: #909399; font-size: 11px; }
.ev-tool-output {
  color: #b0b0b0; font-size: 11px;
  background: #2a2a2a; padding: 6px 8px; border-radius: 4px;
  margin-top: 2px; max-height: 120px; overflow-y: auto;
}
.ev-error { color: #f56c6c; font-weight: bold; }

/* ========== 输入区域 ========== */
.input-area {
  display: flex; flex-direction: column; gap: 8px;
  padding-top: 8px; border-top: 1px solid #ebeef5;
}
.file-preview {
  display: flex; align-items: center; gap: 8px;
  background: #ecf5ff; color: #409eff;
  padding: 6px 12px; border-radius: 6px; font-size: 13px;
  width: fit-content;
}
.input-row { display: flex; gap: 10px; align-items: stretch; }
.upload-btn {
  width: 40px !important; height: auto !important;
  border-radius: 6px !important;
}
.input-row :deep(.el-textarea) { flex: 1; }
.input-row :deep(.el-textarea__inner) { min-height: 46px !important; }
.send-btn {
  width: 72px; font-size: 14px; height: auto;
  border-radius: 6px;
}
</style>

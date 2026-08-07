<template>
  <div class="ai-demo">
    <!-- 聊天消息区域 -->
    <div ref="chatContainer" class="chat-messages">
      <div v-if="messages.length === 0" class="chat-empty">
        <span>👋 输入提示词，开始 AI 对话</span>
      </div>
      <div
        v-for="(msg, index) in messages"
        :key="index"
        class="chat-message"
        :class="msg.role"
      >
        <div class="message-avatar">
          {{ msg.role === 'user' ? '🧑' : '🤖' }}
        </div>
        <div class="message-content">
          <div class="message-role">{{ msg.role === 'user' ? '你' : 'AI 助手' }}</div>
          <div class="message-text">{{ msg.content }}</div>
        </div>
      </div>

      <!-- 加载态 -->
      <div v-if="loading" class="chat-message assistant">
        <div class="message-avatar">🤖</div>
        <div class="message-content">
          <div class="message-role">AI 助手</div>
          <div class="message-text typing-cursor">{{ streamingContent || '思考中...' }}<span class="cursor">|</span></div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="chat-input-area">
      <div class="input-actions">
        <el-button
          :disabled="loading"
          @click="clearMessages"
          size="small"
        >
          清空对话
        </el-button>
      </div>
      <div class="input-row">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="3"
          placeholder="输入你的提示词，按 Ctrl+Enter 发送"
          resize="none"
          :disabled="loading"
          @keydown.ctrl.enter="sendMessage"
        />
        <el-button
          class="send-btn"
          type="primary"
          :disabled="!inputText.trim() || loading"
          :loading="loading"
          @click="sendMessage"
        >
          {{ loading ? 'AI 回复中...' : '发送' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { chatStream } from '@/api/ai'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

const messages = ref<ChatMessage[]>([])
const inputText = ref('')
const loading = ref(false)
const streamingContent = ref('')
const chatContainer = ref<HTMLElement>()

/** 发送消息 */
async function sendMessage() {
  const prompt = inputText.value.trim()
  if (!prompt || loading.value) return

  // 添加用户消息
  messages.value.push({ role: 'user', content: prompt })
  inputText.value = ''

  loading.value = true
  streamingContent.value = ''

  await nextTick()
  scrollToBottom()

  try {
    const response = await chatStream({ prompt })
    console.log('[AI Demo] response status:', response.status, 'ok:', response.ok)

    if (!response.ok) {
      const err = await response.text()
      throw new Error(`API 错误 (${response.status}): ${err}`)
    }

    const reader = response.body?.getReader()
    if (!reader) throw new Error('不支持流式读取')

    const decoder = new TextDecoder()
    let buffer = ''
    let chunkCount = 0

    while (true) {
      const { done, value } = await reader.read()
      if (done) {
        console.log('[AI Demo] stream done, total chunks:', chunkCount, 'buffer leftover:', JSON.stringify(buffer))
        break
      }

      const raw = decoder.decode(value, { stream: true })
      console.log('[AI Demo] raw chunk received:', JSON.stringify(raw))
      buffer += raw

      // 解析 SSE 数据行
      const lines = buffer.split('\n')
      buffer = lines.pop() || '' // 最后一个可能不完整，留到下次

      for (const line of lines) {
        console.log('[AI Demo] line:', JSON.stringify(line))
        if (line.startsWith('data:')) {
          const chunk = line.startsWith('data: ') ? line.slice(6) : line.slice(5)
          chunkCount++
          streamingContent.value += chunk
          await nextTick()
          scrollToBottom()
        }
      }
    }

    // 流结束后保存为助手消息
    console.log('[AI Demo] stream ended, streamingContent length:', streamingContent.value.length)
    if (streamingContent.value) {
      messages.value.push({ role: 'assistant', content: streamingContent.value })
    } else {
      ElMessage.warning('AI 未返回任何内容')
    }
    streamingContent.value = ''
  } catch (err: any) {
    ElMessage.error(err.message || 'AI 请求失败')
    // 如果流式内容已有部分，保留为消息
    if (streamingContent.value) {
      messages.value.push({ role: 'assistant', content: streamingContent.value + '\n\n[回复中断]' })
    }
    streamingContent.value = ''
  } finally {
    loading.value = false
    await nextTick()
    scrollToBottom()
  }
}

/** 清空所有消息 */
function clearMessages() {
  messages.value = []
  streamingContent.value = ''
}

/** 滚动到底部 */
function scrollToBottom() {
  if (chatContainer.value) {
    chatContainer.value.scrollTop = chatContainer.value.scrollHeight
  }
}
</script>

<style scoped>
.ai-demo {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 160px);
  max-width: 900px;
  margin: 0 auto;
  padding: 16px;
  gap: 16px;
}

/* ---- 消息列表 ---- */
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
}

.chat-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
  font-size: 16px;
}

.chat-message {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
}

.chat-message.user {
  flex-direction: row-reverse;
}

.message-avatar {
  font-size: 28px;
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.message-content {
  max-width: 75%;
}

.message-role {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.chat-message.user .message-role {
  text-align: right;
}

.message-text {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.chat-message.assistant .message-text {
  background: #fff;
  border: 1px solid #e4e7ed;
}

.chat-message.user .message-text {
  background: #409eff;
  color: #fff;
}

.typing-cursor {
  font-size: 14px;
  line-height: 1.6;
}

.cursor {
  animation: blink 1s infinite;
}

@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* ---- 输入区域 ---- */
.chat-input-area {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.input-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
}

.input-row {
  display: flex;
  gap: 10px;
  align-items: stretch;
}

.input-row .el-textarea {
  flex: 1;
}

.send-btn {
  width: 80px;
  font-size: 15px;
  height: auto;
}
</style>

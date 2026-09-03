<template>
  <div class="db-analysis">
    <!-- 顶栏：会话管理 -->
    <div class="top-bar">
      <div class="title">数据库性能分析工作室</div>
      <div class="top-actions">
        <el-button type="primary" plain @click="handleNewSession">新会话</el-button>
        <el-button @click="loadSessions">刷新列表</el-button>
        <el-tooltip content="管理数据库连接" placement="bottom">
          <el-button circle @click="showConnDialog = true">
            <el-icon><Setting /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <div class="workspace">
      <!-- 左侧：会话列表 -->
      <div class="left-panel">
        <div class="panel-title">历史会话</div>
        <el-scrollbar class="session-list" v-loading="loadingSessions">
          <div
            class="session-item"
            v-for="s in sessions"
            :key="s.id"
            :class="{ active: currentSessionId === s.id }"
            @click="selectSession(s.id)"
          >
            <div class="session-title">{{ s.sessionTitle }}</div>
            <div class="session-meta">
              <span>{{ formatTime(s.updateTime) }}</span>
              <el-button
                link
                type="danger"
                size="small"
                @click.stop="handleDelete(s.id)"
              >删除</el-button>
            </div>
          </div>
          <el-empty v-if="!loadingSessions && sessions.length === 0" description="暂无会话" :image-size="60" />
        </el-scrollbar>
      </div>

      <!-- 右侧主工作区 -->
      <div class="right-panel">
        <!-- 主分析对话区 -->
        <div class="chat-panel">
          <div class="section-label">主分析对话区</div>
          <el-scrollbar ref="chatScrollRef" class="chat-body" view-class="chat-body-view">
            <div v-for="(msg, idx) in messages" :key="idx" class="msg" :class="msg.role">
              <div class="msg-role">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
              <div class="msg-content" v-if="!msg.tool">
                <pre class="markdown-text">{{ msg.content }}</pre>
              </div>
              <div class="msg-content msg-tool" v-if="msg.tool">
                <div class="tool-name">
                  <el-tag size="small" type="info">工具</el-tag>
                  <span>{{ msg.tool.name }}</span>
                </div>
                <pre class="tool-args">{{ prettyJson(msg.tool.args) }}</pre>
                <pre class="tool-output">{{ msg.tool.output }}</pre>
              </div>
            </div>
            <el-empty v-if="messages.length === 0" description="向 AI 发起数据库性能分析指令" :image-size="64" />
          </el-scrollbar>

          <div class="chat-input">
            <!-- 左侧加号按钮：选择数据库 -->
            <el-popover
              placement="top-start"
              width="280"
              trigger="click"
              :visible="dbPopoverVisible"
              @update:visible="dbPopoverVisible = $event"
            >
              <template #reference>
                <el-tooltip content="选择目标数据库" placement="top">
                  <el-button class="pick-btn" circle :type="selectedAliases.length ? 'primary' : ''">
                    <el-icon><Plus /></el-icon>
                  </el-button>
                </el-tooltip>
              </template>
              <div class="picker-title">目标数据库（多选）</div>
              <el-empty v-if="connections.length === 0" description="请先通过右上角齿轮添加连接" :image-size="48" />
              <div class="picker-list">
                <label
                  class="picker-item"
                  v-for="conn in connections"
                  :key="conn.alias"
                >
                  <el-checkbox
                    :model-value="selectedAliases.includes(conn.alias)"
                    @change="(val: boolean | string | number) => toggleAlias(conn.alias, !!val)"
                  />
                  <span class="dialect-tag">{{ dialectLabel(conn.dialect) }}</span>
                  <span class="alias-name">{{ conn.alias }}</span>
                </label>
              </div>
            </el-popover>

            <!-- 已选数据库铺开展示 -->
            <div class="selected-dbs" v-if="selectedAliases.length">
              <div class="selected-wrap">
                <div
                  class="selected-chip"
                  v-for="alias in selectedAliases"
                  :key="alias"
                >
                  <span class="chip-dialect">{{ dialectLabel(connByAlias(alias)?.dialect || '') }}</span>
                  <span class="chip-alias">{{ alias }}</span>
                  <el-icon class="chip-close" @click="toggleAlias(alias, false)"><Close /></el-icon>
                </div>
              </div>
            </div>

            <!-- 输入框 -->
            <div class="input-area">
              <el-input
                v-model="prompt"
                type="textarea"
                :rows="2"
                placeholder="输入分析指令，如：对比【生产主库】与【迁移Gauss库】中 t_order 表的 Schema 差异并生成 EXPLAIN 计划"
                @keydown.ctrl.enter.prevent="handleSend"
              />
              <div class="chat-actions">
                <el-button
                  type="primary"
                  :loading="running"
                  :disabled="!selectedAliases.length || !prompt.trim()"
                  @click="handleSend"
                >发起分析</el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- HITL 人工审批弹窗 -->
    <ApprovalDialog
      v-model:visible="approvalVisible"
      :pending-tools="pendingTools"
      :loading="approvalLoading"
      @response="handleApprovalResponse"
    />

    <!-- 数据库连接管理弹窗 -->
    <ConnectionDialog
      v-model:visible="showConnDialog"
      :connections="connections"
      @save="addConnection"
      @delete="removeConnection"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { ScrollbarInstance } from 'element-plus'
import { Plus, Setting, Close } from '@element-plus/icons-vue'
import ApprovalDialog from '@/components/db-analysis/ApprovalDialog.vue'
import ConnectionDialog from '@/components/db-analysis/ConnectionDialog.vue'
import {
  listSessions,
  createSession,
  deleteSession,
  analyzeStream,
  approveStream,
  type DbConnectionConfig,
  type SessionDbAnalysis,
} from '@/api/db-analysis'

/* ==================== 状态 ==================== */

const sessions = ref<SessionDbAnalysis[]>([])
const currentSessionId = ref('')
const loadingSessions = ref(false)

// 前端内存中的数据库连接（含临时密码）
const connections = ref<DbConnectionConfig[]>([])

// 当前会话勾选的库别名
const selectedAliases = ref<string[]>([])

const prompt = ref('')

// 对话消息
interface Message {
  role: 'user' | 'assistant'
  content?: string
  tool?: { name: string; args: string; output: string }
}
const messages = ref<Message[]>([])

// 运行状态 / SSE
const running = ref(false)
const chatScrollRef = ref<ScrollbarInstance>()

// HITL 弹窗
const approvalVisible = ref(false)
const approvalLoading = ref(false)
const pendingTools = ref<{ name: string; args: string }[]>([])
const currentSuspendId = ref('')

// 连接配置弹窗
const showConnDialog = ref(false)

// 输入框左侧加号浮窗（选择数据库）显隐
const dbPopoverVisible = ref(false)

/* ==================== 会话管理 ==================== */

async function loadSessions() {
  loadingSessions.value = true
  try {
    sessions.value = await listSessions()
  } finally {
    loadingSessions.value = false
  }
}

async function handleNewSession() {
  const { value } = await ElMessageBox.prompt('请输入会话标题', '新建会话', {
    inputPlaceholder: '如：主从库慢SQL与索引对比分析',
    inputValue: '数据库性能分析',
  }).catch(() => ({ value: '' }))
  if (!value) return
  try {
    const selectedMeta: string = selectedAliases.value.length
      ? JSON.stringify(
          connections.value
            .filter(c => selectedAliases.value.includes(c.alias))
            .map(({ password: _pwd, ...meta }) => meta)
        )
      : '[]'
    const session = await createSession(value, selectedMeta)
    currentSessionId.value = session.id
    messages.value = []
    await loadSessions()
    ElMessage.success('会话已创建')
  } catch (e) {
    // 异常已由拦截器提示
  }
}

function selectSession(id: string) {
  currentSessionId.value = id
  const session = sessions.value.find(s => s.id === id)
  if (session?.contextMessages && session.contextMessages !== '[]') {
    messages.value = [{ role: 'assistant', content: session.contextMessages }]
  } else {
    messages.value = []
  }
  // 从元数据快照还原勾选（密码需用户重新确认，此处仅还原别名）
  try {
    const meta = JSON.parse(session?.selectedDbMeta || '[]') as Pick<
      DbConnectionConfig,
      'alias'
    >[]
    selectedAliases.value = meta.map(m => m.alias)
  } catch {
    selectedAliases.value = []
  }
}

async function handleDelete(id: string) {
  try {
    await ElMessageBox.confirm('确定删除该会话？', '提示', { type: 'warning' })
  } catch {
    return // 用户取消
  }
  try {
    await deleteSession(id)
    if (currentSessionId.value === id) {
      currentSessionId.value = ''
      messages.value = []
    }
    await loadSessions()
    ElMessage.success('已删除')
  } catch {
    // 异常已由拦截器提示
  }
}

/* ==================== 数据库连接 ==================== */

function addConnection(config: DbConnectionConfig) {
  const idx = connections.value.findIndex(c => c.alias === config.alias)
  if (idx >= 0) {
    connections.value.splice(idx, 1, config)
  } else {
    connections.value.push(config)
  }
  if (!selectedAliases.value.includes(config.alias)) {
    selectedAliases.value.push(config.alias)
  }
  ElMessage.success(`已保存连接【${config.alias}】`)
}

function removeConnection(alias: string) {
  connections.value = connections.value.filter(c => c.alias !== alias)
  selectedAliases.value = selectedAliases.value.filter(a => a !== alias)
  ElMessage.success(`已删除连接【${alias}】`)
}

function connByAlias(alias: string): DbConnectionConfig | undefined {
  return connections.value.find(c => c.alias === alias)
}

function toggleAlias(alias: string, checked: boolean) {
  if (checked) {
    if (!selectedAliases.value.includes(alias)) {
      selectedAliases.value.push(alias)
    }
  } else {
    selectedAliases.value = selectedAliases.value.filter(a => a !== alias)
  }
}

function dialectLabel(dialect: string): string {
  const map: Record<string, string> = {
    MYSQL: 'MySQL',
    TDSQL: 'TDSQL',
    GAUSSDB: 'GaussDB',
  }
  return map[dialect] || dialect
}

/* ==================== 分析请求 ==================== */

function activeConnections(): DbConnectionConfig[] {
  return connections.value.filter(c => selectedAliases.value.includes(c.alias))
}

function buildPrompt(extra: string): string {
  const base = prompt.value.trim()
  if (extra) {
    return base ? `${base}\n${extra}` : extra
  }
  return base
}

async function handleSend() {
  const text = buildPrompt('')
  if (!selectedAliases.value.length) {
    ElMessage.warning('请先在左侧勾选目标数据库')
    return
  }
  if (!text) {
    ElMessage.warning('请输入分析指令')
    return
  }
  const conns = activeConnections()
  if (!conns.length) {
    ElMessage.warning('勾选的库无密码信息，请确认连接已配置')
    return
  }

  // 暂存会话（若未创建）
  if (!currentSessionId.value) {
    const selectedMeta = JSON.stringify(
      conns.map(({ password: _pwd, ...meta }) => meta)
    )
    const session = await createSession('数据库性能分析', selectedMeta).catch(() => null)
    if (session) {
      currentSessionId.value = session.id
      await loadSessions()
    } else {
      return
    }
  }

  messages.value.push({ role: 'user', content: text })
  prompt.value = ''
  scrollToBottom()
  running.value = true
  try {
    const resp = await analyzeStream({
      sessionId: currentSessionId.value,
      prompt: text,
      activeDbConnections: conns,
    })
    await consumeStream(resp)
  } catch (e) {
    ElMessage.error(`连接失败: ${(e as Error).message}`)
  } finally {
    running.value = false
  }
}

/* ==================== SSE 流式解析 ==================== */

async function consumeStream(resp: Response) {
  if (!resp.ok || !resp.body) {
    throw new Error(`SSE 连接失败 (${resp.status})`)
  }
  const reader = resp.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  // 启动时初始化一条 AI 占位消息
  let assistantMsg: Message = { role: 'assistant', content: '' }
  messages.value.push(assistantMsg)
  scrollToBottom()

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    // 按 SSE 帧分隔（空行）
    const frames = buffer.split('\n\n')
    buffer = frames.pop() || ''
    for (const frame of frames) {
      handleFrame(frame)
    }
  }
  // 处理残留 buffer
  if (buffer.trim()) handleFrame(buffer)
  scrollToBottom()
}

function handleFrame(frame: string) {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }
  if (!dataLines.length) return
  const data = dataLines.join('\n')
  handleEvent(event, data)
}

function handleEvent(event: string, data: string) {
  switch (event) {
    case 'step_start': {
      const obj = parseJson<{ step: number }>(data)
      if (obj) {
        messages.value.push({ role: 'assistant', content: `【Step ${obj.step}】` })
      }
      break
    }
    case 'thought_chunk': {
      appendAssistantText(data)
      break
    }
    case 'tool_start': {
      const obj = parseJson<{ name: string; args: string }>(data)
      if (obj) {
        messages.value.push({
          role: 'assistant',
          tool: { name: obj.name, args: obj.args, output: '' },
        })
      }
      break
    }
    case 'tool_end': {
      const obj = parseJson<{ name: string; output: string }>(data)
      if (obj) {
        const last = messages.value[messages.value.length - 1]
        if (last?.tool && last.tool.name === obj.name) {
          last.tool.output = obj.output
        }
      }
      break
    }
    case 'text_chunk': {
      appendAssistantText(data)
      break
    }
    case 'suspend': {
      const obj = parseJson<{ suspendId: string; pendingTools: { name: string; args: string }[] }>(data)
      if (obj) {
        currentSuspendId.value = obj.suspendId
        pendingTools.value = obj.pendingTools || []
        approvalVisible.value = true
      }
      break
    }
    case 'complete': {
      break
    }
    case 'error': {
      appendAssistantText(`⚠️ ${data}`)
      break
    }
    default: {
      appendAssistantText(data)
    }
  }
  scrollToBottom()
}

function appendAssistantText(text: string) {
  const last = messages.value[messages.value.length - 1]
  if (last?.role === 'assistant' && last.content !== undefined && !last.tool) {
    last.content += text
  } else {
    messages.value.push({ role: 'assistant', content: text })
  }
}

/* ==================== HITL 审批 ==================== */

async function handleApprovalResponse(approved: boolean, comment: string) {
  if (!currentSuspendId.value) return
  approvalVisible.value = false
  approvalLoading.value = true
  try {
    const resp = await approveStream({
      suspendId: currentSuspendId.value,
      approved,
      comment,
    })
    // 复用同一流式消费，追加 AI 回复
    await consumeStream(resp)
  } catch (e) {
    ElMessage.error(`恢复失败: ${(e as Error).message}`)
  } finally {
    approvalLoading.value = false
    currentSuspendId.value = ''
    running.value = false
  }
}

/* ==================== 工具方法 ==================== */

function parseJson<T>(text: string): T | null {
  try {
    return JSON.parse(text) as T
  } catch {
    return null
  }
}

function prettyJson(text: string): string {
  if (!text) return '{}'
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

function formatTime(t: string): string {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatScrollRef.value?.wrapRef as HTMLElement | undefined
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

const safeSelectedMeta = computed(() => selectedAliases.value)

onMounted(() => {
  loadSessions()
})
</script>

<style scoped>
.db-analysis {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px;
  box-sizing: border-box;
}

.top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.title {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.top-actions {
  display: flex;
  gap: 8px;
}

.workspace {
  flex: 1;
  display: grid;
  grid-template-columns: 240px 1fr;
  gap: 8px;
  min-height: 0;
  overflow: hidden;
}

.left-panel {
  display: flex;
  flex-direction: column;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  background: #fff;
  padding: 8px;
  overflow: hidden;
}

.panel-title {
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.session-list {
  flex: 1;
}

.session-item {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px;
  margin-bottom: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.session-item:hover {
  border-color: #409eff;
}

.session-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}

.session-title {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.right-panel {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 0;
}

.chat-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.chat-body {
  flex: 1;
  min-height: 0;
  margin-bottom: 8px;
}

.msg {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.msg-role {
  flex-shrink: 0;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
}

.msg.user .msg-role {
  background: #409eff;
}

.msg.assistant .msg-role {
  background: #67c23a;
}

.msg-content {
  flex: 1;
  border-radius: 6px;
  padding: 8px 10px;
  background: #f5f7fa;
  max-width: 90%;
}

.msg.user .msg-content {
  background: #ecf5ff;
}

.markdown-text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  font-family: inherit;
  color: #303133;
  line-height: 1.6;
}

.msg-tool {
  background: #fdf6ec;
  border: 1px solid #e6a23c;
}

.tool-name {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 6px;
}

.tool-name span {
  font-weight: 600;
}

.tool-args,
.tool-output {
  max-height: 220px;
  overflow: auto;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 6px;
  font-size: 12px;
  line-height: 1.5;
  margin: 4px 0 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.tool-output {
  background: #f0f9eb;
  border-color: #b3e19d;
}

/* 底部输入区：加号按钮 + 已选库 + 输入框 */
.chat-input {
  display: flex;
  gap: 8px;
  align-items: flex-end;
  flex-wrap: wrap;
}

.pick-btn {
  flex-shrink: 0;
}

/* 加号浮窗（选择数据库） */
.picker-title {
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
  font-size: 13px;
}

.picker-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.picker-item {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.dialect-tag {
  font-size: 11px;
  color: #409eff;
  background: #ecf5ff;
  border-radius: 3px;
  padding: 1px 6px;
}

.alias-name {
  font-size: 13px;
  color: #303133;
}

/* 已选数据库铺开展示 */
.selected-dbs {
  flex-basis: 100%;
  display: flex;
}

.selected-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.selected-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #ecf5ff;
  border: 1px solid #a0cfff;
  border-radius: 4px;
  padding: 2px 6px;
  font-size: 12px;
}

.chip-dialect {
  font-size: 11px;
  color: #409eff;
}

.chip-alias {
  color: #303133;
  font-weight: 500;
}

.chip-close {
  cursor: pointer;
  color: #909399;
}

.chip-close:hover {
  color: #f56c6c;
}

/* 输入框区 */
.input-area {
  flex: 1;
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.input-area .el-input {
  flex: 1;
}

.chat-actions {
  display: flex;
}
</style>
<template>
  <div class="db-analysis">
    <!-- ====== 标题栏 ====== -->
    <div class="demo-header">
      <div class="header-left">
        <h2 class="header-title">🛢️ 数据库性能分析工作室</h2>
        <span class="header-sub">受控探查 · 多库对比 · 交互式诊断</span>
      </div>

      <!-- 右上角操作区：历史会话 / 新会话 / 数据库连接配置 -->
      <div class="header-actions">
        <!-- 历史会话列表（下拉面板） -->
        <el-popover
          placement="bottom-end"
          width="340"
          trigger="click"
          :visible="historyPopVisible"
          @update:visible="onHistoryPopVisible"
        >
          <template #reference>
            <el-button size="small" :disabled="running">
              <el-icon class="btn-icon"><ChatDotRound /></el-icon>
              历史会话
            </el-button>
          </template>

          <div class="history-panel">
            <div class="history-head">
              <span class="history-title">历史会话</span>
              <el-button
                link
                type="primary"
                size="small"
                :loading="loadingSessions"
                @click="loadSessions"
              >
                <el-icon class="btn-icon"><Refresh /></el-icon>刷新
              </el-button>
            </div>

            <el-scrollbar class="history-list" height="260px" v-loading="loadingSessions">
              <div
                v-for="s in sessions"
                :key="s.id"
                class="history-item"
                :class="{ active: currentSessionId === s.id }"
                @click="selectSession(s.id)"
              >
                <div class="history-title-text">{{ s.sessionTitle }}</div>
                <div class="history-meta">
                  <span>{{ formatTime(s.updateTime) }}</span>
                  <el-button
                    link
                    type="danger"
                    size="small"
                    :disabled="running"
                    @click.stop="handleDelete(s.id)"
                  >删除</el-button>
                </div>
              </div>
              <el-empty
                v-if="!loadingSessions && sessions.length === 0"
                description="暂无历史会话，点击「新会话」开始"
                :image-size="56"
              />
            </el-scrollbar>
          </div>
        </el-popover>

        <!-- 新会话 -->
        <el-button type="primary" plain size="small" :disabled="running" @click="handleNewSession">
          新会话
        </el-button>

        <!-- 数据库连接配置 -->
        <el-tooltip content="管理数据库连接" placement="bottom">
          <el-button size="small" circle :disabled="running" @click="showConnDialog = true">
            <el-icon><Setting /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <!-- ====== 当前会话目标数据库（chips） ====== -->
    <div v-if="currentSessionId || selectedAliases.length" class="ctx-bar">
      <span class="ctx-label">目标数据库：</span>
      <template v-if="selectedAliases.length">
        <span
          v-for="alias in selectedAliases"
          :key="alias"
          class="ctx-chip"
        >
          <span class="ctx-dialect">{{ dialectLabel(connByAlias(alias)?.dialect || '') }}</span>
          <span class="ctx-alias">{{ alias }}</span>
          <el-icon class="ctx-close" @click="toggleAlias(alias, false)"><Close /></el-icon>
        </span>
      </template>
      <span v-else class="ctx-empty">未选择，请在下方点击 ＋ 号选择目标数据库</span>
    </div>

    <!-- ====== 消息区域 ====== -->
    <div ref="chatAreaRef" class="chat-area">
      <!-- 空状态 -->
      <div v-if="messages.length === 0 && !running" class="empty-state">
        <div class="empty-icon">🛢️</div>
        <div class="empty-title">数据库性能分析</div>
        <div class="empty-desc">
          配置并勾选目标数据库后，输入分析指令。例如：对比【生产主库】与【迁移Gauss库】中
          t_order 表的 Schema 差异，并分别生成 EXPLAIN 执行计划。
        </div>
        <div class="empty-features">
          <span>多库横向对比</span>
          <span>Schema 差异</span>
          <span>EXPLAIN 计划</span>
          <span>人工审批</span>
        </div>
      </div>

      <!-- 消息列表 -->
      <div
        v-for="(msg, i) in messages"
        :key="i"
        class="chat-msg"
        :class="msg.role"
      >
        <div class="msg-avatar">{{ msg.role === 'user' ? '🧑' : '🤖' }}</div>
        <div class="msg-body">
          <div class="msg-role">{{ msg.role === 'user' ? '你' : 'AI' }}</div>

          <!-- 纯文本内容 -->
          <div v-if="!msg.tool" class="msg-text">
            {{ msg.content || (running && i === messages.length - 1 ? '思考中...' : '') }}
          </div>

          <!-- 工具调用卡片 -->
          <div v-if="msg.tool" class="tool-card">
            <div class="tool-head">
              <el-tag size="small" type="warning" effect="plain">工具</el-tag>
              <span class="tool-name">{{ msg.tool.name }}</span>
            </div>
            <div class="tool-block">
              <div class="tool-block-label">参数</div>
              <pre class="tool-code tool-args">{{ prettyJson(msg.tool.args) }}</pre>
            </div>
            <div class="tool-block">
              <div class="tool-block-label">返回</div>
              <pre class="tool-code tool-output">{{ msg.tool.output || '(执行中...)' }}</pre>
            </div>
          </div>
        </div>
      </div>

      <!-- 运行中的思考气泡 -->
      <div v-if="running" class="chat-msg assistant">
        <div class="msg-avatar">🤖</div>
        <div class="msg-body">
          <div class="msg-role">AI</div>
          <div class="msg-text streaming">正在分析目标数据库<span class="cursor">|</span></div>
        </div>
      </div>
    </div>

    <!-- ====== 输入区域 ====== -->
    <div class="chat-input-area">
      <div class="input-row">
        <!-- 左侧加号按钮：选择目标数据库 -->
        <el-popover
          placement="top-start"
          width="300"
          trigger="click"
          :visible="dbPopoverVisible"
          @update:visible="dbPopoverVisible = $event"
        >
          <template #reference>
            <el-button
              class="db-pick-btn"
              :disabled="running"
              :type="selectedAliases.length ? 'primary' : ''"
            >
              <el-icon :size="18"><Plus /></el-icon>
            </el-button>
          </template>

          <div class="picker-head">
            <span class="picker-title">目标数据库（多选）</span>
            <el-button link type="primary" size="small" @click="openConnFromPicker">管理连接</el-button>
          </div>
          <el-empty v-if="connections.length === 0" description="请先添加数据库连接" :image-size="48" />
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

        <!-- 输入框 -->
        <el-input
          v-model="prompt"
          type="textarea"
          :rows="2"
          :disabled="running"
          placeholder="输入分析指令，Ctrl+Enter 发送…"
          resize="none"
          @keydown.ctrl.enter.prevent="handleSend"
        />

        <!-- 发起分析按钮 -->
        <el-button
          class="send-btn"
          type="primary"
          :disabled="!selectedAliases.length || !prompt.trim()"
          :loading="running"
          @click="handleSend"
        >
          {{ running ? '分析中...' : '发起分析' }}
        </el-button>
      </div>

      <!-- 未选库提示 -->
      <div v-if="!selectedAliases.length" class="input-hint">
        点击左侧 ＋ 号，从已配置连接中选择本次会话的目标数据库（可多选）
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
      @changed="handleConnectionsChanged"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Setting, Close, ChatDotRound, Refresh } from '@element-plus/icons-vue'
import ApprovalDialog from '@/components/db-analysis/ApprovalDialog.vue'
import ConnectionDialog from '@/components/db-analysis/ConnectionDialog.vue'
import {
  listSessions,
  createSession,
  deleteSession,
  listConnections,
  analyzeStream,
  approveStream,
  type DbConnectionConfig,
  type SessionDbAnalysis,
} from '@/api/db-analysis'
import { getCurrentEmpNo } from '@/utils/currentUser'

/* ==================== 状态 ==================== */

const sessions = ref<SessionDbAnalysis[]>([])
const currentSessionId = ref('')
const loadingSessions = ref(false)

// 当前操作员工号（右上角工号切换时联动重载）
const currentEmpNo = ref(getCurrentEmpNo())

// 已保存的数据库连接（由后端落表管理，密码不落前端，勾选时携带 id 由后端解密）
const connections = ref<DbConnectionConfig[]>([])
const loadingConnections = ref(false)

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
const chatAreaRef = ref<HTMLElement>()

// HITL 弹窗
const approvalVisible = ref(false)
const approvalLoading = ref(false)
const pendingTools = ref<{ name: string; args: string }[]>([])
const currentSuspendId = ref('')

// 右上角历史会话下拉
const historyPopVisible = ref(false)

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

function onHistoryPopVisible(val: boolean) {
  historyPopVisible.value = val
  if (val) loadSessions()
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
    historyPopVisible.value = false
    await loadSessions()
    ElMessage.success('会话已创建')
  } catch {
    // 异常已由拦截器提示
  }
}

function selectSession(id: string) {
  if (running.value) return
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
  historyPopVisible.value = false
}

async function handleDelete(id: string) {
  if (running.value) return
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

/* ==================== 数据库连接（落表管理） ==================== */

/** 从后端加载当前操作者已保存的连接（列表不含密码，携带 id 供分析时解密引用）。 */
async function loadConnections() {
  loadingConnections.value = true
  try {
    connections.value = await listConnections()
    // 剔除已不存在的连接勾选
    const validAliases = new Set(connections.value.map(c => c.alias))
    selectedAliases.value = selectedAliases.value.filter(a => validAliases.has(a))
  } catch {
    // 异常已由拦截器提示
  } finally {
    loadingConnections.value = false
  }
}

/** 连接弹窗保存/删除成功后的统一刷新回调。 */
async function handleConnectionsChanged() {
  const wasEmpty = connections.value.length === 0
  await loadConnections()
  // 保留仍存在的勾选；被删除连接的勾选自动失效
  const validAliases = new Set(connections.value.map(c => c.alias))
  selectedAliases.value = selectedAliases.value.filter(a => validAliases.has(a))
  // 首次新增连接后自动勾选首个，便于直接发起分析
  if (wasEmpty && connections.value.length > 0) {
    const first = connections.value[0]
    if (!selectedAliases.value.includes(first.alias)) {
      selectedAliases.value.push(first.alias)
    }
  }
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

function openConnFromPicker() {
  dbPopoverVisible.value = false
  showConnDialog.value = true
}

function dialectLabel(dialect: string): string {
  const map: Record<string, string> = {
    MYSQL: 'MySQL',
    TDSQL: 'TDSQL',
    GAUSSDB: 'GaussDB',
    POSTGRESQL: 'PostgreSQL',
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
    ElMessage.warning('请先点击 ＋ 号选择目标数据库')
    return
  }
  if (!text) {
    ElMessage.warning('请输入分析指令')
    return
  }
  const conns = activeConnections()
  if (!conns.length) {
    ElMessage.warning('勾选的库无连接信息，请重新添加并勾选')
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
    scrollToBottom()
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
    scrollToBottom()
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
    const el = chatAreaRef.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

/** 定时探测右上角工号是否被切换；变化则整体重载当前操作者的数据。 */
let empPollTimer: number | undefined
let initialLoadDone = false

function startEmpPolling() {
  window.clearInterval(empPollTimer)
  empPollTimer = window.setInterval(() => {
    const next = getCurrentEmpNo()
    if (next !== currentEmpNo.value) {
      currentEmpNo.value = next
    }
  }, 1000)
}

// 工号变化 → 清空旧上下文并重新加载新操作者的会话与连接
watch(currentEmpNo, () => {
  currentSessionId.value = ''
  messages.value = []
  selectedAliases.value = []
  connections.value = []
  if (initialLoadDone) {
    loadSessions()
    loadConnections()
  }
})

onMounted(() => {
  initialLoadDone = true
  loadSessions()
  loadConnections()
  startEmpPolling()
})

onBeforeUnmount(() => {
  window.clearInterval(empPollTimer)
})
</script>

<style scoped>
/* ========== 整体布局（参照 AgentDemo 单栏结构） ========== */
.db-analysis {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 160px);
  max-width: 980px;
  margin: 0 auto;
  gap: 12px;
}

/* ========== 标题栏 ========== */
.demo-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}
.header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
  min-width: 0;
}
.header-title {
  margin: 0;
  font-size: 18px;
  color: #303133;
  white-space: nowrap;
}
.header-sub {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.btn-icon {
  margin-right: 3px;
}

/* ========== 当前会话上下文条（目标数据库 chips） ========== */
.ctx-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 13px;
}
.ctx-label {
  color: #909399;
  white-space: nowrap;
}
.ctx-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  background: #ecf5ff;
  border: 1px solid #a0cfff;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 12px;
}
.ctx-dialect {
  font-size: 11px;
  color: #409eff;
}
.ctx-alias {
  color: #303133;
  font-weight: 500;
}
.ctx-close {
  cursor: pointer;
  color: #909399;
}
.ctx-close:hover {
  color: #f56c6c;
}
.ctx-empty {
  color: #c0c4cc;
  font-size: 12px;
}

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
  min-height: 0;
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
.empty-icon {
  font-size: 48px;
}
.empty-title {
  font-size: 18px;
  color: #606266;
  font-weight: 600;
}
.empty-desc {
  font-size: 13px;
  max-width: 420px;
  line-height: 1.7;
}
.empty-features {
  display: flex;
  gap: 8px;
  margin-top: 4px;
  flex-wrap: wrap;
  justify-content: center;
}
.empty-features span {
  background: #ecf5ff;
  color: #409eff;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
}

/* ========== 单条消息 ========== */
.chat-msg {
  display: flex;
  gap: 10px;
}
.chat-msg.user {
  flex-direction: row-reverse;
}
.msg-avatar {
  font-size: 24px;
  flex-shrink: 0;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.msg-body {
  max-width: 82%;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}
.msg-role {
  font-size: 12px;
  color: #909399;
}
.chat-msg.user .msg-role {
  text-align: right;
}

.msg-text {
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 14px;
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
}
.chat-msg.user .msg-text {
  background: #409eff;
  color: #fff;
  border-bottom-right-radius: 2px;
}
.chat-msg.assistant .msg-text {
  background: #f5f7fa;
  border: 1px solid #e4e7ed;
  color: #303133;
  border-bottom-left-radius: 2px;
}
.msg-text.streaming {
  color: #909399;
  font-style: italic;
}
.msg-text .cursor {
  animation: blink 1s infinite;
}
@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

/* ========== 工具调用卡片 ========== */
.tool-card {
  background: #fdf6ec;
  border: 1px solid #e6a23c;
  border-radius: 6px;
  padding: 10px;
  overflow: hidden;
}
.tool-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}
.tool-name {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
}
.tool-block {
  margin-top: 6px;
}
.tool-block-label {
  font-size: 11px;
  color: #909399;
  margin-bottom: 2px;
}
.tool-code {
  margin: 0;
  padding: 8px;
  border-radius: 4px;
  border: 1px solid #ebeef5;
  background: #fff;
  font-family: 'Cascadia Code', 'JetBrains Mono', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 220px;
  overflow: auto;
}
.tool-args {
  color: #606266;
}
.tool-output {
  background: #f0f9eb;
  border-color: #b3e19d;
  color: #303133;
}

/* ========== 输入区域 ========== */
.chat-input-area {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding-top: 8px;
  border-top: 1px solid #ebeef5;
}
.input-row {
  display: flex;
  gap: 10px;
  align-items: stretch;
}
.db-pick-btn {
  width: 40px !important;
  height: auto !important;
  flex-shrink: 0;
}
.input-row :deep(.el-textarea) {
  flex: 1;
  align-self: stretch;
}
.input-row :deep(.el-textarea__inner) {
  min-height: 46px !important;
}
.send-btn {
  width: 96px;
  height: auto;
  font-size: 14px;
  flex-shrink: 0;
}
.input-hint {
  font-size: 12px;
  color: #c0c4cc;
}

/* ========== 加号浮窗（选择数据库） ========== */
.picker-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}
.picker-title {
  font-weight: 600;
  color: #303133;
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

/* ========== 历史会话下拉面板 ========== */
.history-panel {
  width: 100%;
}
.history-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.history-title {
  font-weight: 600;
  color: #303133;
  font-size: 14px;
}
.history-item {
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 6px 8px;
  margin-bottom: 6px;
  cursor: pointer;
  transition: all 0.2s;
}
.history-item:hover {
  border-color: #409eff;
}
.history-item.active {
  border-color: #409eff;
  background: #ecf5ff;
}
.history-title-text {
  font-weight: 600;
  font-size: 13px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.history-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: #909399;
  margin-top: 2px;
}
</style>

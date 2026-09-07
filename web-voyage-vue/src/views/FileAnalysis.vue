<template>
  <div class="file-analysis">
    <!-- 顶部信息条 -->
    <div class="fa-header">
      <div class="fa-title">
        <el-icon class="fa-title-icon"><Files /></el-icon>
        <span>File Analysis · 文件分析</span>
      </div>
      <div class="fa-header-actions">
        <el-tag v-if="currentWorkspace" type="primary" effect="plain" size="large">
          当前工作区: {{ currentWorkspace.dirName }}
        </el-tag>
        <el-button type="primary" :disabled="running" @click="handleCreateWorkspace">
          <el-icon><Plus /></el-icon>&nbsp;新建工作区
        </el-button>
      </div>
    </div>

    <div class="fa-main">
      <!-- ==================== 左侧双面板 ==================== -->
      <aside class="fa-side">
        <div class="fa-side-block">
          <div class="fa-side-head">
            <span>🗂️ 历史工作区</span>
            <el-button text type="primary" :loading="loadingWorkspaces" @click="loadWorkspaces">
              <el-icon><Refresh /></el-icon>
            </el-button>
          </div>
          <div v-loading="loadingWorkspaces" class="fa-ws-list">
            <div
              v-for="ws in workspaces"
              :key="ws.subDirId"
              class="fa-ws-item"
              :class="{ active: ws.subDirId === currentWsId }"
              @click="selectWorkspace(ws)"
            >
              <el-icon class="ws-icon"><Folder /></el-icon>
              <div class="ws-meta">
                <div class="ws-name">{{ ws.dirName }}</div>
                <div class="ws-time">{{ fmtTime(ws.updateTime) }}</div>
              </div>
              <!-- 悬停显示三点：删除该工作区（@click.stop 避免触发上方切换） -->
              <span class="fa-ws-ops">
                <el-tooltip content="删除工作区（连带文件与物理目录）" placement="top">
                  <el-button
                    link
                    type="danger"
                    :icon="MoreFilled"
                    size="small"
                    :disabled="running"
                    @click.stop="handleDeleteWorkspace(ws)"
                  />
                </el-tooltip>
              </span>
            </div>
            <el-empty v-if="!workspaces.length && !loadingWorkspaces" description="暂无工作区" :image-size="48" />
          </div>
        </div>

        <div class="fa-side-block">
          <div class="fa-side-head">
            <span>📁 文件树（当前工作区）</span>
            <el-tooltip
              :content="`上传文件，支持：${ALLOWED_UPLOAD_TEXT}`"
              placement="top"
              :disabled="!currentWorkspace"
            >
              <el-button
                text
                type="primary"
                :loading="uploading"
                :disabled="!currentWorkspace || running"
                @click="openUploader"
              >
                <el-icon v-if="!uploading"><UploadFilled /></el-icon>
              </el-button>
            </el-tooltip>
          </div>
          <div v-loading="loadingFiles" class="fa-file-list">
            <template v-if="currentWorkspace && files.length">
              <div v-for="f in files" :key="f.fileId" class="fa-file-item">
                <span class="fa-file-name" @click="openPreview(f)">
                  <el-icon class="file-icon"><Document /></el-icon>{{ f.fileName }}
                </span>
                <span class="fa-file-ops">
                  <el-tooltip content="预览" placement="top">
                    <el-button link type="primary" :icon="View" size="small" @click="openPreview(f)" />
                  </el-tooltip>
                  <el-tooltip content="下载" placement="top">
                    <el-button link type="success" :icon="Download" size="small" @click="doDownload(f)" />
                  </el-tooltip>
                  <el-tooltip content="删除" placement="top">
                    <el-button link type="danger" :icon="Delete" size="small" @click="handleDeleteFile(f)" />
                  </el-tooltip>
                </span>
              </div>
            </template>
            <el-empty v-else-if="currentWorkspace && !files.length && !loadingFiles" description="暂无文件，请上传" :image-size="48" />
            <el-empty v-else description="请先选择工作区" :image-size="48" />
          </div>
        </div>

        <!-- 隐藏上传触发：accept 过滤 + 前端扩展名校验，避免选中不支持格式后才收到后端 400 -->
        <input
          ref="uploaderRef"
          type="file"
          class="fa-hidden-input"
          :accept="ALLOWED_UPLOAD_ACCEPT"
          @change="onFilePicked"
        />
      </aside>

      <!-- ==================== 右侧主对话区 ==================== -->
      <section class="fa-chat">
        <div ref="chatAreaRef" class="fa-msg-list">
          <template v-if="messages.length">
            <div
              v-for="(m, i) in messages"
              :key="i"
              class="fa-msg"
              :class="m.role === 'user' ? 'is-user' : 'is-assistant'"
            >
              <div class="fa-msg-bubble">
                <!-- 用户消息 -->
                <template v-if="m.role === 'user'">
                  <div class="fa-msg-user">{{ m.content }}</div>
                </template>

                <!-- AI：思考 / 工具卡片 / 最终回答 -->
                <template v-else>
                  <!-- 文本（步骤标题 / 思维过程 / 回答） -->
                  <div v-if="m.content" class="fa-msg-text">
                    <MdPreview
                      v-if="isFinalText(m)"
                      :model-value="m.content"
                      class="fa-msg-md"
                      :htmlPreview="false"
                    />
                    <template v-else>
                      <span v-if="m.stepTitle" class="fa-step-chip">{{ m.content }}</span>
                      <span v-else-if="m.thought" class="fa-thought">{{ m.content }}</span>
                    </template>
                  </div>

                  <!-- 工具调用卡片 -->
                  <div v-if="m.tool" class="fa-tool-card">
                    <div class="fa-tool-head">
                      <el-icon class="tool-icon"><Cpu /></el-icon>
                      <span class="tool-name">{{ m.tool.name }}</span>
                    </div>
                    <div class="fa-tool-body">
                      <div class="fa-tool-label">参数</div>
                      <pre class="fa-tool-pre">{{ m.tool.args }}</pre>
                      <div class="fa-tool-label">输出{{ m.tool.output ? ` (${m.tool.output.length} 字符)` : '' }}</div>
                      <pre class="fa-tool-pre">{{ truncateTool(m.tool.output) }}</pre>
                    </div>
                  </div>

                  <!-- 本轮 Agent 渲染/质检生成的报告下载卡片 -->
                  <div v-if="m.generatedFiles && m.generatedFiles.length" class="fa-gen-cards">
                    <div v-for="gen in m.generatedFiles" :key="gen.fileId" class="fa-gen-card">
                      <el-icon class="gen-icon"><Document /></el-icon>
                      <span class="gen-name">{{ gen.fileName }}</span>
                      <el-button link type="primary" :icon="View" size="small" @click="openPreview(gen)">查看</el-button>
                      <el-button link type="success" :icon="Download" size="small" @click="doDownload(gen)">下载</el-button>
                    </div>
                  </div>
                </template>
              </div>
            </div>
          </template>
          <el-empty v-else description="上传文件后，向智能体提问：质检 / 对比 / 生成报告…" :image-size="72">
            <template #description>
              <div class="fa-empty-tip">
                <p class="fa-empty-title">👋 欢迎使用文件分析智能体</p>
                <p class="fa-empty-sub">
                  左侧新建工作区并上传 <b>.docx / .pdf / .txt / .md / .json / .png</b> 等文件，<br />
                  即可让 AI 质检占位符、文档对比、问答分析或生成分析报告。
                </p>
              </div>
            </template>
          </el-empty>

          <!-- 执行状态滚动提示 -->
          <div v-if="running" class="fa-running">
            <el-icon class="is-loading"><Loading /></el-icon>
            智能体执行中{{ stepsTip ? ` · Step ${stepsTip}` : '' }}…
          </div>
        </div>

        <!-- 输入区：发送按钮置于输入框右侧，高度与输入框一致 -->
        <div class="fa-input-bar">
          <el-input
            v-model="prompt"
            class="fa-input-main"
            type="textarea"
            :rows="3"
            resize="none"
            :disabled="running"
            placeholder="请输入分析指令，例如：对《季度报告.docx》做占位符质检，并生成质检报告"
            @keydown.enter.exact.prevent="handleSend"
          />
          <el-tooltip content="Enter 发送" placement="top">
            <el-button
              class="fa-send-btn"
              type="primary"
              :loading="running"
              :disabled="!prompt.trim()"
              @click="handleSend"
            >
              <span v-if="!running">发送</span>
              <el-icon v-if="!running" class="el-icon--right"><Promotion /></el-icon>
            </el-button>
          </el-tooltip>
        </div>
      </section>
    </div>

    <!-- 文件预览弹窗 -->
    <FilePreviewDialog v-model="previewVisible" :file="previewFile" />
  </div>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Cpu,
  Delete,
  Document,
  Download,
  Files,
  Folder,
  Loading,
  MoreFilled,
  Plus,
  Promotion,
  Refresh,
  UploadFilled,
  View,
} from '@element-plus/icons-vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import FilePreviewDialog from '@/components/file-analysis/FilePreviewDialog.vue'
import {
  createWorkspace,
  deleteFile,
  deleteWorkspace,
  downloadFile,
  getSubDirSession,
  listFiles,
  listWorkspaces,
  textAnalysisChat,
  uploadFile,
  type SessionTextAnalysis,
  type WorkspaceFileItem,
  type WorkspaceSubDir,
} from '@/api/file-analysis'
import { getCurrentEmpNo } from '@/utils/currentUser'

/* ==================== 上传格式白名单（与后端 ALLOWED_EXT 保持一致） ==================== */
const ALLOWED_UPLOAD_EXT = [
  'docx', 'doc', 'pdf', 'txt', 'md', 'markdown', 'json', 'png', 'jpg', 'jpeg', 'xlsx', 'xls',
]
const ALLOWED_UPLOAD_TEXT = 'docx / doc / pdf / txt / md / json / png / jpg / xlsx / xls'
const ALLOWED_UPLOAD_ACCEPT =
  '.doc,.docx,.pdf,.txt,.md,.markdown,.json,.png,.jpg,.jpeg,.xlsx,.xls'

/* ==================== 状态 ==================== */

/** 对话消息 */
interface Message {
  role: 'user' | 'assistant'
  content?: string
  /** 步骤标题消息（step_start 产生，灰底 chip） */
  stepTitle?: boolean
  /** 思维过程文本（thought_chunk 产生） */
  thought?: boolean
  /** 工具调用卡片 */
  tool?: { name: string; args: string; output: string }
  /** 本轮 Agent 生成的报告（complete 事件带回） */
  generatedFiles?: WorkspaceFileItem[]
}

const workspaces = ref<WorkspaceSubDir[]>([])
const loadingWorkspaces = ref(false)
const currentWsId = ref('')
const files = ref<WorkspaceFileItem[]>([])
const loadingFiles = ref(false)
const messages = ref<Message[]>([])
const prompt = ref('')
const running = ref(false)
const stepsTip = ref(0)
const previewVisible = ref(false)
const previewFile = ref<WorkspaceFileItem | null>(null)
const uploaderRef = ref<HTMLInputElement>()
const uploading = ref(false)
const chatAreaRef = ref<HTMLElement>()

const currentWorkspace = computed<WorkspaceSubDir | null>(
  () => workspaces.value.find(w => w.subDirId === currentWsId.value) ?? null
)

const currentEmpNo = ref(getCurrentEmpNo())

/* ==================== 工作区管理 ==================== */

async function loadWorkspaces() {
  loadingWorkspaces.value = true
  try {
    workspaces.value = await listWorkspaces()
    // 若当前工作区仍存在则保留，否则自动选中第一个
    if (currentWsId.value) {
      const alive = workspaces.value.some(w => w.subDirId === currentWsId.value)
      if (!alive) await switchToWorkspace('')
    } else if (workspaces.value.length) {
      await switchToWorkspace(workspaces.value[0].subDirId)
    }
  } catch {
    // 拦截器已提示
  } finally {
    loadingWorkspaces.value = false
  }
}

/** 新建工作区（弹窗可输入名称，默认空 → 后端自动命名） */
async function handleCreateWorkspace() {
  if (running.value) return
  const { value } = await ElMessageBox.prompt('请输入工作区名称（留空自动命名）', '新建工作区', {
    inputPlaceholder: '如：09-07 季度报告质检',
    inputValue: '',
    confirmButtonText: '创建',
    cancelButtonText: '取消',
  }).catch(() => ({ value: undefined }))
  if (value === undefined) return
  try {
    const ws = await createWorkspace(value ?? '')
    await loadWorkspaces()
    await switchToWorkspace(ws.subDirId)
    ElMessage.success('工作区已创建')
  } catch {
    // 拦截器已提示
  }
}

/** 删除指定工作区（左侧历史列表三点入口；二次确认后物理目录 + 数据库级联清理） */
async function handleDeleteWorkspace(ws: WorkspaceSubDir) {
  if (running.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除工作区「${ws.dirName}」？其下所有文件与物理目录将一并清除。`,
      '删除工作区',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await deleteWorkspace(ws.subDirId)
    // 若删除的正是当前工作区，则重置选中状态，交由 loadWorkspaces 自动落到第一个剩余工作区
    if (ws.subDirId === currentWsId.value) {
      currentWsId.value = ''
      files.value = []
      messages.value = []
    }
    await loadWorkspaces()
    ElMessage.success('工作区已删除')
  } catch {
    // 拦截器已提示
  }
}

/** 切换工作区：加载其文件树 + 回显持久化会话历史 */
async function selectWorkspace(ws: WorkspaceSubDir) {
  if (running.value || ws.subDirId === currentWsId.value) return
  await switchToWorkspace(ws.subDirId)
}

async function switchToWorkspace(subDirId: string) {
  currentWsId.value = subDirId
  files.value = []
  messages.value = []
  if (!subDirId) return
  const [fileList, session] = await Promise.allSettled([
    listFiles(subDirId),
    getSubDirSession(subDirId).catch(() => null as SessionTextAnalysis | null),
  ])
  if (fileList.status === 'fulfilled') files.value = fileList.value
  if (session.status === 'fulfilled' && session.value) {
    messages.value = parseHistoryMessages(session.value.contextMessages)
  }
  scrollToBottom()
}

/* ==================== 文件操作 ==================== */

/** 文档/文本类（后端会解析为 Markdown），图片类仅存盘供预览 */
const PARSEABLE_DOC_EXT = ['docx', 'doc', 'pdf', 'xlsx', 'xls', 'txt', 'md', 'markdown', 'json']

async function doUpload(subDirId: string, file: File) {
  uploading.value = true
  try {
    await uploadFile(subDirId, file)
    files.value = await listFiles(subDirId)
    const dot = file.name.lastIndexOf('.')
    const ext = (dot >= 0 ? file.name.slice(dot + 1) : '').toLowerCase()
    ElMessage.success(
      PARSEABLE_DOC_EXT.includes(ext) ? `「${file.name}」上传成功，已预解析内容` : `「${file.name}」上传成功`
    )
  } catch {
    // 拦截器已提示
  } finally {
    uploading.value = false
  }
}

function openUploader() {
  uploaderRef.value?.click()
}

async function onFilePicked(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) {
    if (!currentWsId.value) {
      ElMessage.warning('请先新建 / 选择工作区')
    } else if (uploading.value) {
      ElMessage.warning('正在上传，请稍候')
    } else {
      const dot = file.name.lastIndexOf('.')
      const ext = (dot >= 0 ? file.name.slice(dot + 1) : '').toLowerCase()
      if (!ALLOWED_UPLOAD_EXT.includes(ext)) {
        ElMessage.warning(`暂不支持「${ext || '无扩展名'}」格式，支持：${ALLOWED_UPLOAD_TEXT}`)
      } else {
        await doUpload(currentWsId.value, file)
      }
    }
  }
  input.value = ''
}

async function handleDeleteFile(f: WorkspaceFileItem) {
  if (running.value) return
  try {
    await ElMessageBox.confirm(`确定删除「${f.fileName}」？`, '删除文件', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    })
  } catch {
    return
  }
  try {
    await deleteFile(f.fileId)
    files.value = await listFiles(currentWsId.value)
    ElMessage.success('已删除')
  } catch {
    // 拦截器已提示
  }
}

function openPreview(f: WorkspaceFileItem) {
  previewFile.value = f
  previewVisible.value = true
}

async function doDownload(f: WorkspaceFileItem) {
  try {
    await downloadFile(f.fileId)
  } catch {
    // 已提示
  }
}

/* ==================== 对话 / SSE ==================== */

async function handleSend() {
  const text = prompt.value.trim()
  if (!text || running.value) return
  // 未选择工作区 → 自动创建全新工作区
  if (!currentWsId.value) {
    const ws = await createWorkspace('').catch(() => null)
    if (!ws) return
    await loadWorkspaces()
    await switchToWorkspace(ws.subDirId)
  }

  messages.value.push({ role: 'user', content: text })
  prompt.value = ''
  scrollToBottom()
  running.value = true
  stepsTip.value = 0
  try {
    const resp = await textAnalysisChat({ subDirId: currentWsId.value, prompt: text })
    await consumeStream(resp)
  } catch (e) {
    ElMessage.error(`连接失败: ${(e as Error).message}`)
    appendAnswer(`⚠️ 连接失败：${(e as Error).message}`)
  } finally {
    running.value = false
    stepsTip.value = 0
    scrollToBottom()
  }
}

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
    const frames = buffer.split('\n\n')
    buffer = frames.pop() || ''
    for (const frame of frames) handleFrame(frame)
    scrollToBottom()
  }
  if (buffer.trim()) handleFrame(buffer)
}

function handleFrame(frame: string) {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (!dataLines.length) return
  handleEvent(event, dataLines.join('\n'))
}

async function handleEvent(event: string, data: string) {
  switch (event) {
    case 'step_start': {
      const obj = parseJson<{ step: number }>(data)
      if (obj) {
        stepsTip.value = obj.step
        messages.value.push({ role: 'assistant', content: `Step ${obj.step}`, stepTitle: true })
      }
      break
    }
    case 'thought_chunk':
      appendThought(decodeSseText(data))
      break
    case 'tool_start': {
      const obj = parseJson<{ name: string; args: string }>(data)
      if (obj) {
        messages.value.push({ role: 'assistant', tool: { name: obj.name, args: prettyJson(obj.args), output: '' } })
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
    case 'text_chunk':
      appendAnswer(decodeSseText(data))
      break
    case 'complete': {
      const obj = parseJson<{
        finalText?: string
        sessionId?: string
        generatedFiles?: string[]
        totalSteps?: number
        totalTokens?: number
        totalDurationMs?: number
      }>(data)
      if (obj?.finalText) {
        const answer = lastAnswerMessage()
        // 兜底：若流式 text_chunk 未覆盖最终文本则补齐
        if (!answer || !answer.content?.includes(obj.finalText)) {
          appendAnswer(obj.finalText)
        }
      }
      // 同步 Agent 渲染产物 → 文件树并追加下载卡片
      if (obj?.generatedFiles?.length) {
        try {
          files.value = await listFiles(currentWsId.value)
          const gens = obj.generatedFiles
            .map(id => files.value.find(f => f.fileId === id))
            .filter((f): f is WorkspaceFileItem => Boolean(f))
          const target = lastAnswerMessage()
          if (target && gens.length) target.generatedFiles = gens
        } catch {
          // 文件树刷新失败不阻断完成
        }
      }
      break
    }
    case 'error':
      appendAnswer(`⚠️ ${decodeSseText(data)}`)
      break
    default:
      appendAnswer(decodeSseText(data))
  }
  scrollToBottom()
}

/* ==================== 消息工具方法 ==================== */

/** 查找「当前轮次」（最后一条 user 消息之后）末尾的助手消息，跨轮次不串块。 */
function lastAssistantInTurn(): Message | undefined {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const m = messages.value[i]
    if (m.role === 'user') break
    if (m.role === 'assistant') return m
  }
  return undefined
}

/** 查找「当前轮次」内最近的最终回答消息（非步骤/思维/工具）。 */
function lastAnswerMessage(): Message | undefined {
  for (let i = messages.value.length - 1; i >= 0; i--) {
    const m = messages.value[i]
    if (m.role === 'user') break
    if (m.role === 'assistant' && !m.stepTitle && !m.thought && !m.tool) return m
  }
  return undefined
}

/** 追加助手普通回答文本：与末尾回答块合并（流式累积）。 */
function appendAnswer(text: string) {
  const last = lastAnswerMessage()
  if (last) {
    last.content = (last.content || '') + text
    return
  }
  messages.value.push({ role: 'assistant', content: text })
}

/** 追加思维片段：与末尾思维块合并（流式累积）。 */
function appendThought(text: string) {
  const last = lastAssistantInTurn()
  if (last && last.thought) {
    last.content = (last.content || '') + text
    return
  }
  messages.value.push({ role: 'assistant', content: text, thought: true })
}

/** 判断某助手消息是否为「最终回答」（应作为 Markdown 渲染）：无工具、非步骤、非思维片段 */
function isFinalText(m: Message): boolean {
  return m.role === 'assistant' && !m.tool && !m.stepTitle && !m.thought
}

function truncateTool(text: string): string {
  const t = text || '(无输出)'
  return t.length > 1200 ? `${t.slice(0, 1200)}\n…(已截断)` : t
}

/** 解析后端持久化 context_messages JSON 为前端消息（历史回显）。 */
function parseHistoryMessages(raw?: string): Message[] {
  if (!raw || raw === '[]') return []
  const parsed = parseJson<unknown>(raw)
  if (Array.isArray(parsed)) {
    const list: Message[] = []
    for (const item of parsed) {
      if (!item || typeof item !== 'object') continue
      const rec = item as Record<string, unknown>
      const role = rec.role === 'user' ? 'user' : 'assistant'
      const content = typeof rec.content === 'string' ? rec.content : ''
      if (content) list.push({ role, content })
    }
    return list
  }
  return [{ role: 'assistant', content: String(raw) }]
}

/* ==================== 通用工具 ==================== */

function parseJson<T>(text: string): T | null {
  try {
    return JSON.parse(text) as T
  } catch {
    return null
  }
}

/** SSE 文本 data 可能被二次 JSON.stringify，需反解。 */
function decodeSseText(raw: string): string {
  if (!raw) return ''
  const trimmed = raw.trim()
  if (trimmed.startsWith('"')) {
    const parsed = parseJson<string>(trimmed)
    if (parsed !== null) return parsed
  }
  return raw
}

function prettyJson(text?: string): string {
  if (!text) return ''
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text || ''
  }
}

function fmtTime(t?: string): string {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function scrollToBottom() {
  nextTick(() => {
    const el = chatAreaRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

/* ==================== 生命周期 / 工号监听 ==================== */

let empPollTimer: number | undefined

function startEmpPolling() {
  window.clearInterval(empPollTimer)
  empPollTimer = window.setInterval(() => {
    const next = getCurrentEmpNo()
    if (next !== currentEmpNo.value) {
      currentEmpNo.value = next
      // 工号切换：清空当前操作者内存状态并重载其工作区
      currentWsId.value = ''
      files.value = []
      messages.value = []
      void loadWorkspaces()
    }
  }, 1000)
}

onMounted(() => {
  void loadWorkspaces()
  startEmpPolling()
})

onBeforeUnmount(() => {
  window.clearInterval(empPollTimer)
})
</script>

<style scoped>
.file-analysis {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  gap: 10px;
}

/* ========== 顶部 ========== */
.fa-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 4px 2px;
}

.fa-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}

.fa-title-icon {
  color: var(--el-color-primary);
}

.fa-header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

/* ========== 主体双栏 ========== */
.fa-main {
  flex: 1;
  min-height: 0;
  display: flex;
  gap: 12px;
}

.fa-side {
  width: 300px;
  min-width: 300px;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.fa-side-block {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.fa-side-block:nth-child(1) {
  flex: 0 0 38%;
}

.fa-side-block:nth-child(2) {
  flex: 1 1 62%;
}

.fa-side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-regular);
  border-bottom: 1px solid var(--el-border-color-lighter);
  background: var(--el-fill-color-lighter);
}

.fa-ws-list,
.fa-file-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 4px;
}

/* 工作区条目 */
.fa-ws-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  margin: 2px 0;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.2s;
}

.fa-ws-item:hover {
  background: var(--el-fill-color-light);
}

.fa-ws-item.active {
  background: var(--el-color-primary-light-9);
  box-shadow: inset 2px 0 0 var(--el-color-primary);
}

.ws-icon {
  color: var(--el-color-warning);
  font-size: 16px;
}

.ws-meta {
  flex: 1;
  min-width: 0;
}

.ws-name {
  font-size: 13px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ws-time {
  font-size: 11px;
  color: var(--el-text-color-secondary);
}

/* 悬停显示三点删除入口 */
.fa-ws-ops {
  display: none;
  align-items: center;
  flex-shrink: 0;
}

.fa-ws-item:hover .fa-ws-ops {
  display: inline-flex;
}

/* 文件条目 */
.fa-file-item {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 8px;
  border-radius: 6px;
  transition: background 0.2s;
}

.fa-file-item:hover {
  background: var(--el-fill-color-light);
}

.fa-file-name {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  color: var(--el-text-color-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.fa-file-name:hover {
  color: var(--el-color-primary);
}

.file-icon {
  color: var(--el-color-info);
  flex-shrink: 0;
}

.fa-file-ops {
  display: none;
  align-items: center;
  flex-shrink: 0;
}

.fa-file-item:hover .fa-file-ops {
  display: inline-flex;
}

.fa-hidden-input {
  display: none;
}

/* ========== 右侧对话 ========== */
.fa-chat {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  background: #fff;
  overflow: hidden;
}

.fa-msg-list {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px;
  background: var(--el-fill-color-lighter);
}

.fa-msg {
  display: flex;
  margin-bottom: 14px;
}

.fa-msg.is-user {
  justify-content: flex-end;
}

.fa-msg.is-assistant {
  justify-content: flex-start;
}

.fa-msg-bubble {
  max-width: 86%;
  border-radius: 10px;
  padding: 10px 14px;
  background: #fff;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
}

.fa-msg.is-user .fa-msg-bubble {
  background: var(--el-color-primary);
  color: #fff;
}

.fa-msg-user {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.7;
}

.fa-msg-md {
  --md-color: var(--el-text-color-primary);
}

.fa-step-chip {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  color: var(--el-color-primary);
  background: var(--el-color-primary-light-9);
  border: 1px solid var(--el-color-primary-light-5);
}

.fa-thought {
  display: block;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  line-height: 1.7;
  border-left: 2px solid var(--el-color-info-light-5);
  padding-left: 8px;
  white-space: pre-wrap;
  word-break: break-word;
}

.fa-msg-text {
  line-height: 1.7;
  font-size: 14px;
}

/* 工具卡片 */
.fa-tool-card {
  margin-top: 8px;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 8px;
  overflow: hidden;
  background: #fff;
}

.fa-tool-head {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  background: var(--el-fill-color-lighter);
  font-size: 12px;
  color: var(--el-text-color-regular);
}

.tool-icon {
  color: var(--el-color-primary);
}

.tool-name {
  font-family: 'SFMono-Regular', Consolas, monospace;
  font-weight: 600;
}

.fa-tool-body {
  padding: 8px 10px;
  max-height: 240px;
  overflow: auto;
}

.fa-tool-label {
  font-size: 11px;
  color: var(--el-text-color-secondary);
  margin: 4px 0 2px;
}

.fa-tool-pre {
  margin: 0 0 6px;
  padding: 8px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 130px;
  overflow: auto;
}

/* 生成报告卡片 */
.fa-gen-cards {
  margin-top: 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.fa-gen-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px dashed var(--el-color-success-light-5);
  border-radius: 8px;
  background: var(--el-color-success-light-9);
}

.gen-icon {
  color: var(--el-color-success);
}

.gen-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 运行状态 */
.fa-running {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 0;
  font-size: 13px;
  color: var(--el-color-primary);
}

/* 空态 */
.fa-empty-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--el-text-color-primary);
  margin: 0 0 6px;
}

.fa-empty-sub {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  line-height: 1.8;
  margin: 0;
}

/* 输入区：输入框与发送按钮横向等高排列 */
.fa-input-bar {
  display: flex;
  align-items: stretch;
  gap: 10px;
  padding: 12px;
  border-top: 1px solid var(--el-border-color-lighter);
  background: #fff;
}

.fa-input-main {
  flex: 1;
  min-width: 0;
}

.fa-input-main :deep(.el-textarea__inner) {
  height: 100% !important;
}

.fa-send-btn {
  flex-shrink: 0;
  width: 92px;
  margin-left: 0;
}
</style>

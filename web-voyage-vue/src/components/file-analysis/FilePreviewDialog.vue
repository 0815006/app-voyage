<template>
  <el-dialog
    :model-value="modelValue"
    :title="previewTitle"
    width="860px"
    top="6vh"
    class="file-preview-dialog"
    destroy-on-close
    @update:model-value="onClose"
  >
    <div v-loading="loading" class="preview-body">
      <!-- Markdown / DOCX 预解析文本 -->
      <MdPreview
        v-if="displayKind === 'markdown' && detail?.parsedMarkdown"
        :model-value="detail.parsedMarkdown"
        class="preview-md"
      />
      <!-- JSON 结构化文本（格式化展示） -->
      <pre v-else-if="displayKind === 'json'" class="preview-json">{{ prettyJson(detail?.parsedMarkdown) }}</pre>
      <!-- PDF / 图片：以 blob URL 内嵌展示（fetch 携带 X-Emp-No，规避鉴权绕过） -->
      <iframe v-else-if="displayKind === 'pdf' && rawUrl" :src="rawUrl" class="preview-frame" />
      <img v-else-if="displayKind === 'image' && rawUrl" :src="rawUrl" class="preview-img" alt="预览图片" />
      <el-empty
        v-else
        :description="emptyDesc"
      >
        <template v-if="file">
          <el-button type="primary" link @click="doDownload">
            <el-icon><Download /></el-icon>&nbsp;下载查看
          </el-button>
        </template>
      </el-empty>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import { Download } from '@element-plus/icons-vue'
import { getCurrentEmpNo } from '@/utils/currentUser'
import {
  previewFile,
  previewFileUrl,
  downloadFile,
  type WorkspaceFileDetail,
  type WorkspaceFileItem,
} from '@/api/file-analysis'

/**
 * 文件轻量化预览弹窗（文件分析模块专用）。
 * - DOCX / MD / TXT：直接渲染后端 ai-tool-doc 预解析的 Markdown（与 AI 视角一致）
 * - JSON：以格式化纯文本展示
 * - PDF / 图片：fetch 携带 X-Emp-No 拉取 blob 后内嵌展示（iframe / img 无法带请求头）
 * - 其余类型：提示暂不支持在线预览，引导下载
 */

const props = defineProps<{
  modelValue: boolean
  file: WorkspaceFileItem | null
}>()

const emit = defineEmits<{
  (e: 'update:modelValue', val: boolean): void
}>()

const detail = ref<WorkspaceFileDetail | null>(null)
const rawUrl = ref('')
const loading = ref(false)

/** 文件类型 → 展示形态 */
type DisplayKind = 'markdown' | 'json' | 'pdf' | 'image' | 'none' | 'unsupported'
const displayKind = computed<DisplayKind>(() => {
  const type = (props.file?.fileType || '').toUpperCase()
  if (type === 'PDF') return 'pdf'
  if (['PNG', 'JPG', 'JPEG', 'GIF', 'SVG', 'BMP', 'WEBP'].includes(type)) return 'image'
  if (type === 'JSON') return 'json'
  if (['DOCX', 'DOC', 'TXT', 'MD', 'MARKDOWN', 'XLSX', 'XLS'].includes(type)) return 'markdown'
  return 'unsupported'
})

const previewTitle = computed(() => {
  const base = props.file ? `${props.file.fileName} (${formatBytes(props.file.fileSize)})` : '文件预览'
  return displayKind.value === 'unsupported' ? `${base} · 暂不支持在线预览` : base
})

const emptyDesc = computed(() => {
  if (displayKind.value === 'unsupported') return '暂不支持在线预览，请下载后查看'
  if (!detail.value?.parsedMarkdown) return '该文件暂无预解析内容，可下载后查看'
  return '暂无内容'
})

/* ==================== 打开时加载详情 ==================== */

watch(
  () => [props.modelValue, props.file?.fileId] as const,
  ([open]) => {
    if (open && props.file) {
      void load()
    }
  }
)

async function load() {
  if (!props.file) return
  loading.value = true
  detail.value = null
  rawUrl.value = ''
  try {
    detail.value = await previewFile(props.file.fileId)
    // 二进制类内容通过 blob URL 内嵌（携带 X-Emp-No）
    if (displayKind.value === 'pdf' || displayKind.value === 'image') {
      rawUrl.value = await fetchBlobUrl(previewFileUrl(props.file.fileId))
    }
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

/** fetch 带 X-Emp-No 拉取原始文件流为 blob objectURL（iframe/img 无法注入请求头）。 */
async function fetchBlobUrl(url: string): Promise<string> {
  const resp = await fetch(url, { headers: { 'X-Emp-No': getCurrentEmpNo() } })
  if (!resp.ok) throw new Error(`加载失败 HTTP ${resp.status}`)
  return URL.createObjectURL(await resp.blob())
}

function prettyJson(text?: string | null): string {
  if (!text) return '{}'
  try {
    return JSON.stringify(JSON.parse(text), null, 2)
  } catch {
    return text
  }
}

function formatBytes(size?: number): string {
  if (!size || size <= 0) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

async function doDownload() {
  if (!props.file) return
  try {
    await downloadFile(props.file.fileId)
  } catch {
    // 提示已由调用处处理
  }
}

function onClose(val: boolean) {
  if (rawUrl.value) URL.revokeObjectURL(rawUrl.value)
  rawUrl.value = ''
  emit('update:modelValue', val)
}
</script>

<style scoped>
.preview-body {
  min-height: 320px;
  max-height: 68vh;
  overflow: auto;
  border: 1px solid var(--el-border-color-lighter);
  border-radius: 6px;
  background: #fff;
}

.preview-md {
  padding: 8px 16px;
}

.preview-json {
  margin: 0;
  padding: 14px 16px;
  font-family: 'SFMono-Regular', Consolas, 'Liberation Mono', Menlo, monospace;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.preview-frame {
  display: block;
  width: 100%;
  height: 64vh;
  border: 0;
}

.preview-img {
  display: block;
  max-width: 100%;
  max-height: 64vh;
  margin: 0 auto;
  object-fit: contain;
}
</style>

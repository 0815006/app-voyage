<template>
  <el-dialog
    :model-value="visible"
    width="560px"
    :close-on-click-modal="false"
    :show-close="false"
    title="⚠️ 人工审批 / 安全拦截"
    class="approval-dialog"
    @update:model-value="onVisibleChange"
  >
    <div class="approval-body">
      <el-alert type="warning" :closable="false" show-icon>
        <template #title>Agent 尝试在已授权数据库中执行需审批的高危探查操作</template>
      </el-alert>

      <div class="pending-tools" v-loading="loading">
        <template v-if="pendingTools.length">
          <div class="tool-card" v-for="(tool, idx) in pendingTools" :key="idx">
            <div class="tool-name">
              <el-tag type="danger" effect="dark">工具调用</el-tag>
              <span class="tool-label">{{ tool.name }}</span>
            </div>
            <pre class="tool-args">{{ prettyArgs(tool.args) }}</pre>
          </div>
          <div class="risk-hint">
            说明：上述操作可能触发写操作、全表扫描或耗时较长的探查。请审慎决定是否放行。
          </div>
        </template>
        <template v-else>
          <div class="empty-tip">等待审批信息……</div>
        </template>
      </div>

      <el-input
        v-model="comment"
        type="textarea"
        :rows="3"
        maxlength="200"
        show-word-limit
        placeholder="审批意见（可留空）"
      />
    </div>

    <template #footer>
      <div class="approval-footer">
        <el-button type="danger" :loading="loading" @click="handleReject">拒绝执行</el-button>
        <el-button type="primary" :loading="loading" @click="handleApprove">允许并继续</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'

/** 待审批工具调用描述 */
export interface PendingTool {
  name: string
  args: string
}

const props = defineProps<{
  visible: boolean
  pendingTools: PendingTool[]
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'response', approved: boolean, comment: string): void
  (e: 'update:visible', value: boolean): void
}>()

const comment = ref('')

// 每次弹窗展示时清空审批意见
watch(
  () => props.visible,
  val => {
    if (val) comment.value = ''
  }
)

function onVisibleChange(val: boolean) {
  emit('update:visible', val)
}

function prettyArgs(args: string): string {
  if (!args) return '{}'
  try {
    return JSON.stringify(JSON.parse(args), null, 2)
  } catch {
    return args
  }
}

function handleApprove() {
  emit('response', true, comment.value)
  ElMessage.success('已允许执行，正在继续分析…')
}

function handleReject() {
  emit('response', false, comment.value)
  ElMessage.info('已拒绝执行，Agent 将调整方案')
}
</script>

<style scoped>
.approval-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.pending-tools {
  min-height: 120px;
}

.tool-card {
  border: 1px solid #e6a23c;
  border-radius: 6px;
  padding: 10px 12px;
  margin-bottom: 10px;
  background: #fdf6ec;
}

.tool-name {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.tool-label {
  font-weight: 600;
  color: #303133;
}

.tool-args {
  max-height: 180px;
  overflow: auto;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  padding: 8px;
  font-size: 12px;
  line-height: 1.6;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.risk-hint {
  font-size: 12px;
  color: #e6a23c;
}

.empty-tip {
  color: #909399;
  text-align: center;
  padding: 24px 0;
}

.approval-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
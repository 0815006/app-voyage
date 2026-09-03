<template>
  <el-dialog
    :model-value="visible"
    width="600px"
    :title="editingAlias ? '编辑数据库连接' : '数据库连接管理'"
    @update:model-value="onVisibleChange"
    @open="openHandler"
    @closed="closedHandler"
  >
    <!-- 模式一：连接列表管理 -->
    <div v-if="!editingAlias" class="conn-manager">
      <div class="conn-list">
        <el-empty v-if="connections.length === 0" description="尚未维护任何数据库连接" :image-size="64" />
        <div
          v-for="conn in connections"
          :key="conn.alias"
          class="conn-item"
        >
          <div class="conn-info">
            <el-tag size="small" type="info">{{ dialectLabel(conn.dialect) }}</el-tag>
            <span class="conn-alias">{{ conn.alias }}</span>
            <span class="conn-url">{{ conn.host }}:{{ conn.port }}/{{ conn.dbName }}</span>
          </div>
          <div class="conn-actions">
            <el-button link type="primary" size="small" @click="startEdit(conn)">编辑</el-button>
            <el-button
              link
              type="success"
              size="small"
              :loading="testingAlias === conn.alias"
              @click="handleTestConnection(conn)"
            >测试连接</el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 模式二：新建/编辑连接表单 -->
    <div v-else class="conn-form">
      <el-form :model="form" label-width="100px" @submit.prevent>
        <el-form-item label="连接别名" required>
          <el-input v-model="form.alias" placeholder="如：生产主库" maxlength="50" />
        </el-form-item>

        <el-form-item label="数据库方言" required>
          <el-select v-model="form.dialect" style="width: 100%">
            <el-option label="MySQL" value="MYSQL" />
            <el-option label="TDSQL" value="TDSQL" />
            <el-option label="GaussDB" value="GAUSSDB" />
          </el-select>
        </el-form-item>

        <el-form-item label="主机地址" required>
          <el-input v-model="form.host" placeholder="如：192.168.1.100" />
        </el-form-item>

        <el-form-item label="端口" required>
          <el-input-number v-model="form.port" :min="1" :max="65535" style="width: 100%" />
        </el-form-item>

        <el-form-item label="数据库名" required>
          <el-input v-model="form.dbName" placeholder="如：order_db" />
        </el-form-item>

        <el-form-item label="用户名" required>
          <el-input v-model="form.user" placeholder="只读账号更安全" />
        </el-form-item>

        <el-form-item label="密码" required>
          <el-input v-model="form.password" type="password" show-password placeholder="仅存前端内存，不入库" />
        </el-form-item>
      </el-form>
    </div>

    <!-- 统一的页脚：根据模式切换按钮组 -->
    <template #footer>
      <template v-if="!editingAlias">
        <el-button type="primary" plain @click="startEdit()">＋ 新建连接</el-button>
        <el-button @click="emit('update:visible', false)">关闭</el-button>
      </template>
      <template v-else>
        <el-button
          type="danger"
          plain
          :disabled="!isEditingExisting"
          @click="handleDelete"
        >删除</el-button>
        <el-button
          type="success"
          plain
          :loading="testingAlias === '__current__'"
          @click="handleTestConnection(form)"
        >测试连接</el-button>
        <el-button @click="cancelEdit">取消</el-button>
        <el-button type="primary" @click="handleSave">保存</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import type { DbConnectionConfig } from '@/api/db-analysis'
import { testConnection } from '@/api/db-analysis'

const props = defineProps<{
  visible: boolean
  connections: DbConnectionConfig[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'save', config: DbConnectionConfig): void
  (e: 'delete', alias: string): void
}>()

// 当前处于编辑模式的别名；空字符串表示列表模式
const editingAlias = ref<string>('')
const isEditingExisting = computed(() =>
  props.connections.some(c => c.alias === editingAlias.value)
)

const testingAlias = ref<string>('')

const emptyForm = (): DbConnectionConfig => ({
  alias: '',
  dialect: 'MYSQL',
  host: '',
  port: 3306,
  dbName: '',
  user: '',
  password: '',
})

const form = reactive<DbConnectionConfig>(emptyForm())

function onVisibleChange(val: boolean) {
  emit('update:visible', val)
}

function openHandler() {
  editingAlias.value = ''
  Object.assign(form, emptyForm())
}

function closedHandler() {
  editingAlias.value = ''
  testingAlias.value = ''
}

function startEdit(conn?: DbConnectionConfig) {
  if (conn) {
    editingAlias.value = conn.alias
    Object.assign(form, { ...conn })
  } else {
    editingAlias.value = '__new__'
    Object.assign(form, emptyForm())
  }
}

function cancelEdit() {
  editingAlias.value = ''
  Object.assign(form, emptyForm())
}

function validateForm(): boolean {
  if (!form.alias || !form.host || !form.dbName || !form.user || !form.password) {
    ElMessage.warning('请完整填写连接信息（尤其是临时密码）')
    return false
  }
  return true
}

async function handleTestConnection(config: DbConnectionConfig) {
  // 表单模式下点击“测试连接”：先校验必填字段，且使用当前表单内容
  const fromForm = editingAlias.value !== ''
  if (fromForm) {
    if (!validateForm()) return
  }
  const mark = config.alias || '__current__'
  testingAlias.value = mark
  try {
    const result = await testConnection({ ...config })
    if (result.success) {
      ElMessage.success(`连接可用：${result.alias}`)
    } else {
      ElMessage.error(`连接失败：${result.message || '未知原因'}`)
    }
  } catch (e) {
    ElMessage.error(`连接测试失败：${(e as Error).message}`)
  } finally {
    testingAlias.value = ''
  }
}

function handleSave() {
  if (!validateForm()) return
  emit('save', { ...form })
  // 保存成功后回到列表模式
  cancelEdit()
}

async function handleDelete() {
  const alias = editingAlias.value
  if (!isEditingExisting.value) return
  emit('delete', alias)
  cancelEdit()
}

function dialectLabel(dialect: string): string {
  const map: Record<string, string> = {
    MYSQL: 'MySQL',
    TDSQL: 'TDSQL',
    GAUSSDB: 'GaussDB',
  }
  return map[dialect] || dialect
}
</script>

<style scoped>
.conn-manager {
  min-height: 200px;
}

.conn-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 420px;
  overflow: auto;
}

.conn-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px;
}

.conn-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
}

.conn-alias {
  font-weight: 600;
  color: #303133;
  white-space: nowrap;
}

.conn-url {
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conn-actions {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.conn-form {
  padding-bottom: 4px;
}
</style>
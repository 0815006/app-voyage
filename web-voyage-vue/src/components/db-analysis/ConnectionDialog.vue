<template>
  <el-dialog
    :model-value="visible"
    width="600px"
    :title="isEditingExisting ? '编辑数据库连接' : editingAlias === '__new__' ? '新建数据库连接' : '数据库连接管理'"
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
          :key="conn.id || conn.alias"
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
            <el-button
              link
              type="danger"
              size="small"
              :loading="deletingAlias === conn.alias"
              @click="handleDeleteFromList(conn)"
            >删除</el-button>
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
            <el-option label="PostgreSQL" value="POSTGRESQL" />
          </el-select>
        </el-form-item>

        <el-form-item label="主机地址" required>
          <el-input v-model="form.host" placeholder="如：192.168.1.100" />
        </el-form-item>

        <el-form-item label="端口" required>
          <el-input-number
            v-model="form.port"
            :min="1"
            :max="65535"
            :step="100"
            style="width: 100%"
          />
        </el-form-item>

        <el-form-item label="数据库名" required>
          <el-input v-model="form.dbName" placeholder="如：order_db" />
        </el-form-item>

        <el-form-item label="用户名" required>
          <el-input v-model="form.user" placeholder="只读账号更安全" />
        </el-form-item>

        <el-form-item label="密码" :required="!isEditingExisting">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isEditingExisting ? '不修改请留空，后端保留原密文' : '将加密存储于服务端'"
          />
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
          :loading="saving"
          @click="handleDelete"
        >删除</el-button>
        <el-button
          type="success"
          plain
          :loading="testingAlias === '__current__'"
          @click="handleTestConnection(form)"
        >测试连接</el-button>
        <el-button :disabled="saving" @click="cancelEdit">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { DbConnectionConfig } from '@/api/db-analysis'
import { testConnection, saveConnection, deleteConnection } from '@/api/db-analysis'

const props = defineProps<{
  visible: boolean
  connections: DbConnectionConfig[]
}>()

const emit = defineEmits<{
  (e: 'update:visible', value: boolean): void
  (e: 'changed'): void
}>()

// 当前处于编辑模式的标记：'' = 列表模式；'__new__' = 新建；其他为编辑中的连接别名
const editingAlias = ref<string>('')
const isEditingExisting = computed(() =>
  props.connections.some(c => c.alias === editingAlias.value)
)

// 编辑中的原连接（用于识别 id / 判断是否改密），无则视为新建
const editingOrigin = ref<DbConnectionConfig | null>(null)

const saving = ref(false)
const testingAlias = ref<string>('')
const deletingAlias = ref<string>('')

const emptyForm = (): DbConnectionConfig => ({
  id: undefined,
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
  editingOrigin.value = null
  Object.assign(form, emptyForm())
}

function closedHandler() {
  editingAlias.value = ''
  editingOrigin.value = null
  testingAlias.value = ''
  deletingAlias.value = ''
  saving.value = false
}

function startEdit(conn?: DbConnectionConfig) {
  if (conn) {
    editingAlias.value = conn.alias
    editingOrigin.value = { ...conn }
    // 列表中的连接不含密码（脱敏），编辑时置空让用户决定是否改密
    Object.assign(form, { ...conn, password: '' })
  } else {
    editingAlias.value = '__new__'
    editingOrigin.value = null
    Object.assign(form, emptyForm())
  }
}

function cancelEdit() {
  editingAlias.value = ''
  editingOrigin.value = null
  Object.assign(form, emptyForm())
}

function validateForm(): boolean {
  if (!form.alias.trim()) {
    ElMessage.warning('请填写连接别名')
    return false
  }
  if (!form.host.trim()) {
    ElMessage.warning('请填写主机地址')
    return false
  }
  if (!form.dbName.trim()) {
    ElMessage.warning('请填写数据库名')
    return false
  }
  if (!form.user.trim()) {
    ElMessage.warning('请填写用户名')
    return false
  }
  if (!isEditingExisting.value && !form.password) {
    ElMessage.warning('新建连接必须填写密码（加密存储于服务端）')
    return false
  }
  return true
}

/**
 * 从连接对象提取提交给后端的白名单字段。
 * 忽略 createTime/updateTime/empNo 等只读与审计字段，避免意外回写。
 */
function toPayload(src: DbConnectionConfig): DbConnectionConfig {
  return {
    id: src.id,
    alias: src.alias,
    dialect: src.dialect,
    host: src.host,
    port: src.port,
    dbName: src.dbName,
    user: src.user,
    password: src.password || '',
  }
}

/** 取「编辑中的表单字段」，叠加原连接兜底（保存 id 与未改字段）。 */
function formSource(): DbConnectionConfig {
  return { ...(editingOrigin.value || emptyForm()), ...form }
}

async function handleTestConnection(config: DbConnectionConfig) {
  const fromForm = editingAlias.value !== ''
  if (fromForm) {
    if (!validateForm()) return
  }
  const mark = (fromForm ? form.alias : config.alias) || '__current__'
  testingAlias.value = mark
  try {
    // 表单模式：表单覆盖原连接字段（密码留空时后端按 id 自动解密原密码）
    const source = fromForm ? formSource() : config
    const result = await testConnection(toPayload(source))
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

async function handleSave() {
  if (!validateForm()) return
  saving.value = true
  try {
    await saveConnection(toPayload(formSource()))
    ElMessage.success(editingOrigin.value?.id ? '连接已更新' : '连接已保存')
    emit('changed')
    cancelEdit()
  } catch {
    // 异常已由拦截器提示
  } finally {
    saving.value = false
  }
}

async function confirmDelete(id: string, alias: string): Promise<boolean> {
  try {
    await ElMessageBox.confirm(`确定删除连接【${alias}】？`, '提示', { type: 'warning' })
    return true
  } catch {
    return false
  }
}

/** 列表模式：直接删除 */
async function handleDeleteFromList(conn: DbConnectionConfig) {
  if (!conn.id) return
  if (!(await confirmDelete(conn.id, conn.alias))) return
  deletingAlias.value = conn.alias
  try {
    await deleteConnection(conn.id)
    ElMessage.success(`已删除连接【${conn.alias}】`)
    emit('changed')
  } catch {
    // 异常已由拦截器提示
  } finally {
    deletingAlias.value = ''
  }
}

/** 编辑模式：删除当前编辑的连接 */
async function handleDelete() {
  const origin = editingOrigin.value
  if (!origin?.id || !isEditingExisting.value) return
  if (!(await confirmDelete(origin.id, origin.alias))) return
  saving.value = true
  try {
    await deleteConnection(origin.id)
    ElMessage.success(`已删除连接【${origin.alias}】`)
    emit('changed')
    cancelEdit()
  } catch {
    // 异常已由拦截器提示
  } finally {
    saving.value = false
  }
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

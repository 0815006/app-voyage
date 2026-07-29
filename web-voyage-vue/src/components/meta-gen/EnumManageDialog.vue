<template>
  <el-dialog title="枚举库管理" v-model="visible" width="700px" append-to-body @close="handleClose">
    <div style="margin-bottom: 12px; text-align: right">
      <el-button type="primary" size="small" :icon="Plus" @click="handleAdd">新增枚举</el-button>
    </div>
    <el-table :data="enumList" border size="small" v-loading="loading">
      <el-table-column prop="enumKey" label="Key" width="150" />
      <el-table-column prop="enumName" label="名称" width="150" />
      <el-table-column label="枚举项 (val=desc)">
        <template #default="scope">
          <template v-if="scope.row.items">
            <el-tag v-for="(item, idx) in parseItems(scope.row.items)" :key="idx" size="small" style="margin:2px">
              {{ item.val }}={{ item.desc }}
            </el-tag>
          </template>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button type="primary" link size="small" @click="handleEdit(scope.row)">编辑</el-button>
          <el-button type="danger" link size="small" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog :title="editForm.id ? '编辑枚举' : '新增枚举'" v-model="innerVisible" width="500px" append-to-body>
      <el-form :model="editForm" label-width="80px" size="small">
        <el-form-item label="Key">
          <el-input v-model="editForm.enumKey" placeholder="如 sex" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="editForm.enumName" placeholder="如 性别" />
        </el-form-item>
        <el-form-item label="枚举项">
          <div v-for="(item, idx) in editItems" :key="idx" style="display:flex;gap:8px;margin-bottom:4px">
            <el-input v-model="item.val" placeholder="值" style="width:100px" />
            <el-input v-model="item.desc" placeholder="描述" style="flex:1" />
            <el-button type="danger" link :icon="Delete" @click="editItems.splice(idx, 1)" />
          </div>
          <el-button type="primary" link :icon="Plus" @click="editItems.push({ val: '', desc: '' })">添加项</el-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button size="small" @click="innerVisible = false">取消</el-button>
        <el-button size="small" type="primary" @click="handleSaveEnum">保存</el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Delete } from '@element-plus/icons-vue'
import { listEnums, saveEnum, deleteEnum } from '@/api/meta-gen'

interface EnumItem {
  val: string
  desc: string
}

interface EnumRecord {
  id?: number
  enumKey: string
  enumName: string
  items?: string
}

interface EditForm extends EnumRecord {
  id?: number
}

const props = defineProps<{
  dialogVisible: boolean
}>()

const emit = defineEmits<{
  (e: 'update:dialogVisible', v: boolean): void
  (e: 'changed'): void
}>()

const visible = ref(props.dialogVisible)

watch(() => props.dialogVisible, (v) => {
  visible.value = v
  if (v) loadData()
})

watch(visible, (v) => {
  emit('update:dialogVisible', v)
})

const enumList = ref<EnumRecord[]>([])
const loading = ref(false)
const innerVisible = ref(false)
const editForm = ref<EditForm>({})
const editItems = ref<EnumItem[]>([])

function parseItems(json: string): EnumItem[] {
  try { return JSON.parse(json) || [] } catch { return [] }
}

function loadData() {
  loading.value = true
  listEnums().then((res: EnumRecord[]) => { enumList.value = res || [] }).finally(() => { loading.value = false })
}

function handleAdd() {
  editForm.value = {}
  editItems.value = [
    { val: '0', desc: '男' },
    { val: '1', desc: '女' },
  ]
  innerVisible.value = true
}

function handleEdit(row: EnumRecord) {
  editForm.value = { ...row }
  editItems.value = parseItems(row.items || '')
  innerVisible.value = true
}

function handleSaveEnum() {
  const items = editItems.value.filter(i => i.val)
  editForm.value.items = JSON.stringify(items)
  saveEnum(editForm.value).then(() => {
    ElMessage.success('保存成功')
    innerVisible.value = false
    loadData()
    emit('changed')
  })
}

function handleDelete(row: EnumRecord) {
  ElMessageBox.confirm('确定删除枚举 "' + row.enumKey + '" 吗？', '提示', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消',
  }).then(() => {
    deleteEnum(row.id!).then(() => {
      ElMessage.success('删除成功')
      loadData()
      emit('changed')
    })
  }).catch(() => {})
}

function handleClose() {
  emit('update:dialogVisible', false)
}
</script>

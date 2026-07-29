<template>
  <div class="resource-check-container">
    <h2 class="page-title">资源核查报告</h2>

    <!-- 查询表单 -->
    <el-card shadow="hover" style="margin-bottom: 20px">
      <el-form :inline="true" size="small" style="display: flex; align-items: center">
        <el-form-item label="产品标识（批次）" style="margin-bottom: 0">
          <el-select
            v-model="selectedKey"
            placeholder="请选择产品标识（批次）"
            style="width: 280px"
            filterable
            clearable
            @change="handleQuery"
          >
            <el-option
              v-for="item in productOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item style="margin-bottom: 0">
          <el-button type="primary" @click="handleQuery" :loading="loading">
            查询
          </el-button>
        </el-form-item>
        <div style="flex: 1" />
        <el-form-item style="margin-bottom: 0; margin-right: 0">
          <el-button type="success" @click="handleUpload">
            <el-icon style="margin-right: 4px"><Upload /></el-icon>
            上传资源
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 汇总表格 -->
    <el-row :gutter="20" v-if="summaryList.length > 0 || summaryListApply.length > 0">
      <el-col :span="12">
        <el-card shadow="hover" style="margin-bottom: 20px">
          <template #header>
            <span style="font-weight: bold">部署方案-资源总量汇总</span>
          </template>
          <el-table
            :data="summaryList"
            border
            size="small"
            style="width: 100%"
            :default-sort="{ prop: 'deploymentLocation', order: 'ascending' }"
          >
            <el-table-column prop="deploymentLocation" label="部署地点" width="120" sortable />
            <el-table-column prop="systemPlatform" label="系统平台" />
            <el-table-column prop="hostCount" label="机器台数" width="80" />
            <el-table-column prop="totalCpu" label="总CPU" width="80" />
            <el-table-column prop="totalMemoryGb" label="总内存" width="80" />
            <el-table-column prop="totalStorageGb" label="总存储" width="80" />
          </el-table>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover" style="margin-bottom: 20px">
          <template #header>
            <span style="font-weight: bold">申请表-资源总量汇总</span>
          </template>
          <el-table
            :data="summaryListApply"
            border
            size="small"
            style="width: 100%"
            :default-sort="{ prop: 'deploymentLocation', order: 'ascending' }"
          >
            <el-table-column prop="deploymentLocation" label="部署地点" width="120" sortable />
            <el-table-column prop="systemPlatform" label="系统平台" />
            <el-table-column prop="hostCount" label="机器台数" width="80" />
            <el-table-column prop="totalCpu" label="总CPU" width="80" />
            <el-table-column prop="totalMemoryGb" label="总内存" width="80" />
            <el-table-column prop="totalStorageGb" label="总存储" width="80" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 部署方案-文件级资源汇总 -->
    <el-card v-if="fileSummaryList && fileSummaryList.length > 0" shadow="hover" style="margin-top: 20px">
      <template #header>
        <span style="font-weight: bold">部署方案-文件级资源汇总（按原始文件名分组）</span>
      </template>
      <el-collapse>
        <el-collapse-item
          v-for="(group, idx) in fileSummaryList"
          :key="idx"
          :name="idx"
        >
          <template #title>
            <span style="font-weight: bold; color: #409eff; flex: 1">
              📄 {{ group.originalFileName }}
              <el-tag size="small" style="margin-left: 8px">
                上传 {{ group.uploadCount }} 次
              </el-tag>
            </span>
            <el-button
              size="small"
              type="danger"
              @click.stop="handleDeleteFile(group.originalFileName)"
              :loading="deleting === group.originalFileName"
              style="margin-left: 0"
            >
              <el-icon style="margin-right: 4px"><Delete /></el-icon>
              删除
            </el-button>
          </template>
          <el-table
            :data="group.summary"
            border
            size="small"
            style="width: 100%"
            :default-sort="{ prop: 'deploymentLocation', order: 'ascending' }"
          >
            <el-table-column prop="deploymentLocation" label="部署地点" width="150" sortable />
            <el-table-column prop="systemPlatform" label="系统平台" width="180" />
            <el-table-column prop="hostCount" label="主机数量" width="100" />
            <el-table-column prop="totalCpu" label="CPU总核数" width="100" />
            <el-table-column prop="totalMemoryGb" label="内存总量(GB)" width="120" />
            <el-table-column prop="totalStorageGb" label="存储总量(GB)" width="120" />
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <!-- 申请表-文件级资源汇总 -->
    <el-card v-if="fileSummaryListApply && fileSummaryListApply.length > 0" shadow="hover" style="margin-top: 20px">
      <template #header>
        <span style="font-weight: bold">申请表-文件级资源汇总（按原始文件名分组）</span>
      </template>
      <el-collapse>
        <el-collapse-item
          v-for="(group, idx) in fileSummaryListApply"
          :key="idx"
          :name="'apply-' + idx"
        >
          <template #title>
            <span style="font-weight: bold; color: #67c23a; flex: 1">
              📄 {{ group.originalFileName }}
              <el-tag size="small" type="success" style="margin-left: 8px">
                上传 {{ group.uploadCount }} 次
              </el-tag>
            </span>
            <el-button
              size="small"
              type="danger"
              @click.stop="handleDeleteFile(group.originalFileName)"
              :loading="deleting === group.originalFileName"
              style="margin-left: 0"
            >
              <el-icon style="margin-right: 4px"><Delete /></el-icon>
              删除
            </el-button>
          </template>
          <el-table
            :data="group.summary"
            border
            size="small"
            style="width: 100%"
            :default-sort="{ prop: 'deploymentLocation', order: 'ascending' }"
          >
            <el-table-column prop="deploymentLocation" label="部署地点" width="150" sortable />
            <el-table-column prop="systemPlatform" label="系统平台" width="180" />
            <el-table-column prop="hostCount" label="主机数量" width="100" />
            <el-table-column prop="totalCpu" label="CPU总核数" width="100" />
            <el-table-column prop="totalMemoryGb" label="内存总量(GB)" width="120" />
            <el-table-column prop="totalStorageGb" label="存储总量(GB)" width="120" />
          </el-table>
        </el-collapse-item>
      </el-collapse>
    </el-card>

    <!-- 部署方案-明细表格 -->
    <el-card v-if="detailList.length > 0" shadow="hover" style="margin-top: 20px">
      <template #header>
        <span style="font-weight: bold">部署方案-资源明细配置（相同配置已合并）</span>
      </template>
      <el-table
        :data="detailList"
        border
        size="small"
        style="width: 100%"
        :default-sort="{ prop: 'deploymentLocation', order: 'ascending' }"
      >
        <el-table-column prop="deploymentLocation" label="部署地点" width="150" sortable />
        <el-table-column prop="systemPlatform" label="系统平台" width="180" />
        <el-table-column prop="partitionUsage" label="分区用途" width="380" />
        <el-table-column prop="count" label="机器台数" width="80" />
        <el-table-column prop="cpuCores" label="CPU核心数" width="100" />
        <el-table-column prop="memoryGb" label="内存(GB)" width="100" />
        <el-table-column prop="dedicatedStorageGb" label="独占存储(GB)" width="120" />
        <el-table-column prop="sanStorageGb" label="SAN存储(GB)" width="120" />
        <el-table-column prop="nasStorageGb" label="NAS存储(GB)" width="120" />
      </el-table>
    </el-card>

    <!-- 申请表-资源明细配置 -->
    <el-card v-if="detailListApply.length > 0" shadow="hover" style="margin-top: 20px">
      <template #header>
        <span style="font-weight: bold">申请表-资源明细配置（相同配置已合并）</span>
      </template>
      <el-table
        :data="detailListApply"
        border
        size="small"
        style="width: 100%"
        :default-sort="{ prop: 'deploymentLocation', order: 'ascending' }"
      >
        <el-table-column prop="deploymentLocation" label="部署地点" width="150" sortable />
        <el-table-column prop="systemPlatform" label="系统平台" width="180" />
        <el-table-column prop="partitionUsage" label="分区用途" width="380" />
        <el-table-column prop="count" label="机器台数" width="80" />
        <el-table-column prop="cpuCores" label="CPU核心数" width="100" />
        <el-table-column prop="memoryGb" label="内存(GB)" width="100" />
        <el-table-column prop="dedicatedStorageGb" label="独占存储(GB)" width="120" />
        <el-table-column prop="sanStorageGb" label="SAN存储(GB)" width="120" />
        <el-table-column prop="nasStorageGb" label="NAS存储(GB)" width="120" />
      </el-table>
    </el-card>

    <!-- 无数据提示 -->
    <el-empty
      v-if="!loading && summaryList.length === 0 && detailList.length === 0 && summaryListApply.length === 0 && detailListApply.length === 0"
      description="暂无数据"
    />

    <!-- 上传弹窗 -->
    <UploadResourceDialog ref="uploadDialogRef" @refresh="handleQuery" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Upload, Delete } from '@element-plus/icons-vue'
import {
  checkResources,
  deleteResourceByFile,
  getProductIds,
  type ResourceSummary,
  type ResourceDetail,
  type FileSummaryGroup,
} from '@/api/resource'
import UploadResourceDialog from '@/components/resource-check/UploadResourceDialog.vue'

// ===================== 响应式数据 =====================

const selectedKey = ref('')
const productOptions = ref<Array<{ value: string; label: string }>>([])
const loading = ref(false)
const deleting = ref('')
const summaryList = ref<ResourceSummary[]>([])
const detailList = ref<ResourceDetail[]>([])
const fileSummaryList = ref<FileSummaryGroup[]>([])
const summaryListApply = ref<ResourceSummary[]>([])
const detailListApply = ref<ResourceDetail[]>([])
const fileSummaryListApply = ref<FileSummaryGroup[]>([])

// ===================== Template Refs =====================

const uploadDialogRef = ref<{
  init: (productId: string, batchNo: string) => void
} | null>(null)

// ===================== 计算属性 =====================

const productId = computed(() => {
  if (!selectedKey.value) return ''
  const idx = selectedKey.value.lastIndexOf('|')
  return idx >= 0 ? selectedKey.value.substring(0, idx) : selectedKey.value
})

const batchNo = computed(() => {
  if (!selectedKey.value) return ''
  const idx = selectedKey.value.lastIndexOf('|')
  return idx >= 0 ? selectedKey.value.substring(idx + 1) : ''
})

// ===================== 生命周期 =====================

onMounted(() => {
  fetchProductIds()
})

// ===================== 方法 =====================

async function fetchProductIds() {
  try {
    const rawList = await getProductIds()
    productOptions.value = rawList.map((item: string) => {
      const idx = item.lastIndexOf('|')
      const pid = idx >= 0 ? item.substring(0, idx) : item
      const bno = idx >= 0 ? item.substring(idx + 1) : ''
      return {
        value: item,
        label: pid + '（' + bno + '）',
      }
    })
    if (
      productOptions.value.length > 0 &&
      !productOptions.value.find((o) => o.value === selectedKey.value)
    ) {
      selectedKey.value = productOptions.value[0].value
    }
    if (selectedKey.value) {
      handleQuery()
    }
  } catch (error) {
    console.error('获取产品列表失败', error)
  }
}

async function handleQuery() {
  if (!productId.value || !batchNo.value) {
    ElMessage.warning('请选择产品标识（批次）')
    return
  }

  loading.value = true
  summaryList.value = []
  detailList.value = []
  fileSummaryList.value = []
  summaryListApply.value = []
  detailListApply.value = []
  fileSummaryListApply.value = []

  try {
    const [dataPlan, dataApply] = await Promise.all([
      checkResources(productId.value, batchNo.value, '部署方案'),
      checkResources(productId.value, batchNo.value, '资源申请表'),
    ])

    summaryList.value = dataPlan.summaryList || []
    detailList.value = dataPlan.detailList || []
    fileSummaryList.value = dataPlan.fileSummaryList || []

    summaryListApply.value = dataApply.summaryList || []
    detailListApply.value = dataApply.detailList || []
    fileSummaryListApply.value = dataApply.fileSummaryList || []

    ElMessage.success('查询成功')
  } catch (error) {
    ElMessage.error('网络请求失败，请检查接口是否可达')
    console.error(error)
  } finally {
    loading.value = false
  }
}

function handleUpload() {
  uploadDialogRef.value?.init(productId.value, batchNo.value)
}

async function handleDeleteFile(originalFileName: string) {
  try {
    await ElMessageBox.confirm(
      `确定删除文件 "${originalFileName}" 的所有资源数据吗？此操作不可恢复`,
      '警告',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      }
    )
  } catch (error: unknown) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    return
  }

  deleting.value = originalFileName

  try {
    const res = await deleteResourceByFile(
      productId.value,
      batchNo.value,
      originalFileName
    )
    ElMessage.success(
      (res as { message?: string }).message || '删除成功'
    )
    handleQuery()
  } catch (error) {
    ElMessage.error('删除失败，请检查网络或权限')
    console.error(error)
  } finally {
    deleting.value = ''
  }
}
</script>

<style scoped>
.resource-check-container {
  padding: 20px;
  margin: 16px;
  height: calc(100% - 32px);
  overflow-y: auto;
  border-radius: 14px;
  background: linear-gradient(180deg, #ffffff 0%, #f8fbff 100%);
  border: 1px solid #dbe7f7;
  box-shadow: 0 10px 22px rgba(21, 52, 105, 0.08);
  box-sizing: border-box;
}

.page-title {
  margin: 0 0 18px;
  font-size: 22px;
  color: #1f2d3d;
}

:deep(.el-card) {
  border-radius: 12px;
  border: 1px solid #e2ebf8;
  box-shadow: 0 6px 16px rgba(25, 56, 108, 0.06);
}

:deep(.el-card__header) {
  background: #f6f9ff;
  border-bottom: 1px solid #e6eef9;
}

:deep(.el-table) {
  border-radius: 10px;
  overflow: hidden;
}

:deep(.el-table th) {
  background: #f3f8ff;
  color: #42526a;
}

:deep(.el-input__inner),
:deep(.el-button) {
  border-radius: 8px;
}

:deep(.el-collapse-item__header) {
  font-weight: 600;
  color: #334155;
}

.resource-check-container::-webkit-scrollbar {
  width: 6px;
}

.resource-check-container::-webkit-scrollbar-thumb {
  background: #c8d8f0;
  border-radius: 8px;
}
</style>

<template>
  <el-dialog title="编辑任务信息" v-model="visible" width="95%" top="5vh" @close="handleClose">
    <div class="dialog-layout">
      <!-- 左侧填写展示区 -->
      <div class="input-area">
        <div class="recognition-section">
          <div class="paste-container">
            <div class="paste-box">
              <div class="paste-label">标题信息粘贴区</div>
              <el-input type="textarea" :rows="4" v-model="pasteData.titleText" placeholder="粘贴标题信息..."></el-input>
            </div>
            <div class="paste-box">
              <div class="paste-label">时间与人员信息粘贴区</div>
              <el-input type="textarea" :rows="6" v-model="pasteData.timeText" placeholder="粘贴时间与人员信息..."></el-input>
            </div>
            <div class="paste-box">
              <div class="paste-label">需求与项目信息粘贴区</div>
              <el-input type="textarea" :rows="6" v-model="pasteData.reqText" placeholder="粘贴需求与项目信息..."></el-input>
            </div>
            <div class="recognition-btn">
              <el-button type="warning" size="small" :icon="MagicStick" @click="handleRecognize">识别并填充到右侧</el-button>
            </div>
          </div>

          <div class="edit-form">
            <el-form :model="form" label-width="110px" size="default">
              <el-row :gutter="15">
                <el-col :span="12"><el-form-item label="任务名称"><el-input v-model="form.taskName"></el-input></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="测试任务编号"><el-input v-model="form.testTaskNo"></el-input></el-form-item></el-col>
              </el-row>
              <el-row :gutter="15">
                <el-col :span="12"><el-form-item label="生产任务编号"><el-input v-model="form.prodTaskNo"></el-input></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="产品标识"><el-input v-model="form.productId" disabled></el-input></el-form-item></el-col>
              </el-row>
              <el-row :gutter="15">
                <el-col :span="12"><el-form-item label="批次"><el-input v-model="form.batchNo" disabled></el-input></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="需求编号"><el-input v-model="form.reqNo"></el-input></el-form-item></el-col>
              </el-row>
              <el-row :gutter="15">
                <el-col :span="12"><el-form-item label="项目名称"><el-input v-model="form.projName"></el-input></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="项目编号"><el-input v-model="form.projNo"></el-input></el-form-item></el-col>
              </el-row>
              <el-row :gutter="15">
                <el-col :span="12"><el-form-item label="测试部门"><el-input v-model="form.testDept"></el-input></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="开发部门"><el-input v-model="form.devDept"></el-input></el-form-item></el-col>
              </el-row>
              <el-row :gutter="15">
                <el-col :span="12">
                  <el-form-item label="开始时间">
                    <el-date-picker v-model="form.startTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width: 100%;"></el-date-picker>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="结束时间">
                    <el-date-picker v-model="form.endTime" type="datetime" value-format="yyyy-MM-dd HH:mm:ss" style="width: 100%;"></el-date-picker>
                  </el-form-item>
                </el-col>
              </el-row>
              <el-row :gutter="15">
                <el-col :span="8"><el-form-item label="性能测试经理"><el-input v-model="form.perfManager"></el-input></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="测试架构师"><el-input v-model="form.testArch"></el-input></el-form-item></el-col>
                <el-col :span="8"><el-form-item label="项目经理"><el-input v-model="form.projectManager"></el-input></el-form-item></el-col>
              </el-row>
              <el-form-item label="填报人员范围">
                <el-input type="textarea" v-model="form.recorderRange" placeholder="7位员工号，逗号分隔"></el-input>
              </el-form-item>
            </el-form>
            <div class="form-footer">
              <el-button type="primary" @click="handleUpdate" :loading="loading">更新信息</el-button>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧指导提示区 -->
      <div class="guide-area">
        <div class="guide-content">
          <h3>填写指南</h3>
          <p><strong>1. 标题信息识别：</strong> 复制系统标题区域文本，包含任务名称、编号、产品等。</p>
          <p><strong>2. 时间人员识别：</strong> 复制包含批次、部门、起止时间、各角色负责人的文本块。</p>
          <p><strong>3. 需求项目识别：</strong> 复制包含需求编号、项目名称、项目编号的文本块。</p>
          <el-divider></el-divider>
          <p class="tip">提示：点击"识别并填充"后，系统会自动解析文本并填入右侧表单，您可以继续手动微调。</p>
          <div v-for="i in 10" :key="i" class="placeholder-text">补充说明内容占位...</div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { recognizeTaskInfo, saveTask } from '@/api/performance'

interface TaskForm {
  id?: number
  taskName?: string
  testTaskNo?: string
  prodTaskNo?: string
  productId?: string
  batchNo?: string
  reqNo?: string
  projName?: string
  projNo?: string
  testDept?: string
  devDept?: string
  startTime?: string
  endTime?: string
  perfManager?: string
  testArch?: string
  projectManager?: string
  recorderRange?: string
  creatorId?: string
  status?: number
}

interface PasteData {
  titleText: string
  timeText: string
  reqText: string
}

const emit = defineEmits<{
  refresh: []
}>()

const visible = ref<boolean>(false)
const loading = ref<boolean>(false)
const form = ref<TaskForm>({})

const pasteData = reactive<PasteData>({
  titleText: '',
  timeText: '',
  reqText: '',
})

function init(task: TaskForm) {
  visible.value = true
  form.value = { ...task }
  pasteData.titleText = ''
  pasteData.timeText = ''
  pasteData.reqText = ''
}

async function handleRecognize() {
  try {
    const recognized = await recognizeTaskInfo({ ...pasteData })
    // 合并识别结果，但不覆盖 ID, productId, batchNo 等核心字段
    const { id, productId, batchNo, creatorId, status } = form.value
    form.value = { ...form.value, ...(recognized as Record<string, unknown>), id, productId, batchNo, creatorId, status }
    ElMessage.success('识别成功')
  } catch {
    // error handled by interceptor
  }
}

async function handleUpdate() {
  loading.value = true
  try {
    await saveTask(form.value)
    ElMessage.success('更新成功')
    emit('refresh')
    visible.value = false
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}

function handleClose() {
  form.value = {}
}

defineExpose({ init })
</script>

<style scoped>
.dialog-layout {
  display: flex;
  height: 75vh;
}
.input-area {
  flex: 2;
  padding-right: 20px;
  border-right: 1px solid #ebeef5;
  overflow-y: auto;
}
.guide-area {
  flex: 1;
  padding-left: 20px;
  overflow-y: auto;
  background-color: #fafafa;
}
.recognition-section {
  display: flex;
  gap: 20px;
}
.paste-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.edit-form {
  flex: 1.5;
}
.paste-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.recognition-btn {
  margin-top: 10px;
  text-align: center;
}
.form-footer {
  margin-top: 20px;
  text-align: right;
}
.guide-content h3 {
  margin-top: 0;
  color: #303133;
}
.guide-content p {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}
.tip {
  color: #e6a23c !important;
  font-weight: bold;
}
.placeholder-text {
  color: #c0c4cc;
  font-size: 12px;
  margin-top: 10px;
}
</style>

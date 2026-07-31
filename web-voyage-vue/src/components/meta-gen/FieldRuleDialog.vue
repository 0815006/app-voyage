<template>
  <el-dialog
    :title="isEdit ? '编辑字段规则' : '新增字段'"
    v-model="visible"
    width="650px"
    append-to-body
    @close="handleClose"
  >
    <el-form :model="form" label-width="110px" size="small">
      <el-form-item label="区块">
        <el-select v-model="form.section" style="width:100%">
          <el-option label="文件名 (FILENAME)" value="FILENAME" />
          <el-option label="文件头 (HEADER)" value="HEADER" />
          <el-option label="文件体 (BODY)" value="BODY" />
          <el-option label="文件尾 (FOOTER)" value="FOOTER" />
        </el-select>
      </el-form-item>
      <el-form-item label="变量名 (Key)">
        <el-input v-model="form.fieldKey" placeholder="唯一标识，如 userName" />
      </el-form-item>
      <el-form-item label="字段描述">
        <el-input v-model="form.fieldName" placeholder="业务描述" />
      </el-form-item>
      <el-form-item label="层级">
        <el-radio-group v-model="form.level">
          <el-radio :value="1">一级字段</el-radio>
          <el-radio :value="2">二级字段（子字段）</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item v-if="form.level === 2" label="父字段">
        <el-select v-model="form.parentId" style="width:100%" placeholder="选择一级字段">
          <el-option
            v-for="p in parentFields"
            :key="p.id"
            :label="p.fieldKey + ' (' + p.fieldName + ')'"
            :value="p.id"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="排序号 (sortIndex)">
        <el-input-number v-model="form.sortIndex" :min="0" :max="99999" style="width:100%" />
      </el-form-item>
      <el-form-item label="长度 (字节)">
        <el-input-number v-model="form.length" :min="1" :max="9999" style="width:100%" />
        <span class="length-hint">(Unit: Bytes)</span>
      </el-form-item>
      <el-form-item label="补齐方向">
        <el-select v-model="form.paddingDirection" style="width:100%">
          <el-option label="不补齐" value="NONE" />
          <el-option label="左补齐" value="LEFT" />
          <el-option label="右补齐" value="RIGHT" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.paddingDirection !== 'NONE'" label="补齐字符">
        <el-select v-model="form.paddingChar" style="width:100%">
          <el-option label="空格 ( )" value=" " />
          <el-option label="零 (0)" value="0" />
        </el-select>
      </el-form-item>
      <el-divider>规则配置</el-divider>
      <el-form-item label="规则类型">
        <el-select v-model="form.ruleType" style="width:100%" @change="onRuleTypeChange">
          <el-option label="固定值 (FIXED)" value="FIXED" />
          <el-option label="日期 (DATE)" value="DATE" />
          <el-option label="枚举 (ENUM)" value="ENUM" />
          <el-option label="引用文件 (REF_FILE)" value="REF_FILE" />
          <el-option label="引用字段 (REF_FIELD)" value="REF_FIELD" />
          <el-option label="序列号 (SEQ)" value="SEQ" />
          <el-option label="汇总金额 (SUM)" value="SUM" />
          <el-option label="统计行数 (COUNT)" value="COUNT" />
          <el-option label="随机值 (RANDOM)" value="RANDOM" />
          <el-option label="金额 (AMOUNT)" value="AMOUNT" />
          <el-option label="表达式 (EXPRESSION)" value="EXPRESSION" />
        </el-select>
      </el-form-item>

      <!-- FIXED -->
      <el-form-item v-if="form.ruleType === 'FIXED'" label="固定值">
        <el-input v-model="fixedValue" placeholder="输入此字段的固定默认值" />
      </el-form-item>

      <!-- DATE -->
      <template v-if="form.ruleType === 'DATE'">
        <el-form-item label="日期格式">
          <el-select v-model="dateFormat" style="width:100%" allow-create filterable placeholder="输入或选择日期格式">
            <el-option label="YYYYMMDD (20260512)" value="YYYYMMDD" />
            <el-option label="YYYY-MM-dd (2026-05-12)" value="YYYY-MM-dd" />
            <el-option label="yyyyMMddHHmmss (20260512103847)" value="yyyyMMddHHmmss" />
            <el-option label="yyyy-MM-dd HH:mm:ss" value="yyyy-MM-dd HH:mm:ss" />
            <el-option label="yyMMdd (260512)" value="yyMMdd" />
            <el-option label="MMdd (0512)" value="MMdd" />
            <el-option label="HHmmss (103847)" value="HHmmss" />
          </el-select>
        </el-form-item>
      </template>

      <!-- ENUM -->
      <el-form-item v-if="form.ruleType === 'ENUM'" label="枚举库">
        <el-select v-model="form.refEnumKey" style="width:100%" placeholder="选择枚举Key">
          <el-option v-for="k in enumKeys" :key="k" :label="k" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="form.ruleType === 'ENUM'" label="默认值">
        <el-input v-model="enumDefault" placeholder="如不填则随机取值" />
      </el-form-item>

      <!-- REF_FIELD -->
      <el-form-item v-if="form.ruleType === 'REF_FIELD'" label="引用字段">
        <el-select v-model="form.refFieldKey" style="width:100%" placeholder="选择同报文内前面定义的字段">
          <el-option v-for="key in availableFieldKeys" :key="key" :label="key" :value="key" />
        </el-select>
      </el-form-item>

      <!-- REF_FILE -->
      <el-form-item v-if="form.ruleType === 'REF_FILE'" label="引用文件">
        <el-select v-model="form.refFileId" style="width:100%" placeholder="选择素材文件">
          <el-option v-for="rf in refFiles" :key="rf.id" :label="rf.refName" :value="rf.id" />
        </el-select>
      </el-form-item>

      <!-- SEQ -->
      <el-form-item v-if="form.ruleType === 'SEQ'" label="序列号Key">
        <el-input v-model="form.refSequenceKey" placeholder="如 batch_seq" />
      </el-form-item>

      <!-- AMOUNT -->
      <template v-if="form.ruleType === 'AMOUNT'">
        <el-form-item label="精度 (Precision)">
          <el-input-number v-model="amountConfig.precision" :min="0" :max="10" style="width:100%" />
        </el-form-item>
        <el-form-item label="整数位 (Scale)">
          <el-input-number v-model="amountConfig.scale" :min="1" :max="30" style="width:100%" />
        </el-form-item>
        <el-form-item label="小数模式">
          <el-radio-group v-model="amountConfig.decimalMode">
            <el-radio value="IMPLICIT">隐式 (无小数点)</el-radio>
            <el-radio value="EXPLICIT">显式 (带小数点)</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="带符号位">
          <el-switch v-model="amountConfig.signed" />
        </el-form-item>
      </template>

      <!-- RANDOM -->
      <template v-if="form.ruleType === 'RANDOM'">
        <el-form-item label="随机模式">
          <el-select v-model="randomConfig.mode" style="width:100%">
            <el-option label="数字 (DIGIT)" value="DIGIT" />
            <el-option label="汉字 (CHINESE)" value="CHINESE" />
            <el-option label="UUID" value="UUID" />
          </el-select>
        </el-form-item>
      </template>

      <!-- EXPRESSION -->
      <el-form-item v-if="form.ruleType === 'EXPRESSION'" label="表达式函数">
        <el-select v-model="exprConfig.func" style="width:100%">
          <el-option label="拼接 (CONCAT)" value="CONCAT" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button size="small" @click="visible = false">取消</el-button>
      <el-button size="small" type="primary" @click="handleConfirm">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'

interface FieldForm {
  id?: number
  section: string
  fieldKey: string
  fieldName: string
  level: number
  parentId: number | null
  sortIndex: number
  length: number | null
  paddingDirection: string
  paddingChar: string
  ruleType: string
  refFieldKey: string | null
  refEnumKey: string | null
  refSequenceKey: string | null
  refFileId: number | null
  ruleConfigJson: string | null
}

interface AmountConfig {
  precision: number
  scale: number
  decimalMode: string
  signed: boolean
}

interface RandomConfig {
  mode: string
}

interface ExprConfig {
  func: string
  params: string[]
}

interface ParentField {
  id: number
  fieldKey: string
  fieldName: string
}

interface AllFieldItem {
  fieldKey: string
}

interface RefFileItem {
  id: number
  refName: string
}

const props = defineProps<{
  dialogVisible: boolean
  fieldData: FieldForm | null
  parentFields: ParentField[]
  allFields: AllFieldItem[]
  enumKeys: string[]
  refFiles: RefFileItem[]
  currentIndex: number
}>()

const emit = defineEmits<{
  (e: 'update:dialogVisible', v: boolean): void
  (e: 'confirm', data: FieldForm): void
}>()

function getDefaultForm(): FieldForm {
  return {
    section: 'BODY',
    fieldKey: '',
    fieldName: '',
    level: 1,
    parentId: null,
    sortIndex: 0,
    length: null,
    paddingDirection: 'NONE',
    paddingChar: ' ',
    ruleType: 'FIXED',
    refFieldKey: null,
    refEnumKey: null,
    refSequenceKey: null,
    refFileId: null,
    ruleConfigJson: null,
  }
}

const form = ref<FieldForm>(getDefaultForm())
const fixedValue = ref('')
const dateFormat = ref('YYYYMMDD')
const amountConfig = ref<AmountConfig>({ precision: 2, scale: 14, decimalMode: 'IMPLICIT', signed: false })
const randomConfig = ref<RandomConfig>({ mode: 'DIGIT' })
const exprConfig = ref<ExprConfig>({ func: 'CONCAT', params: [] })
const enumDefault = ref('')

const isEdit = computed(() => !!(props.fieldData && props.fieldData.id))

const visible = computed({
  get: () => props.dialogVisible,
  set: (v: boolean) => emit('update:dialogVisible', v),
})

const availableFieldKeys = computed(() => {
  const keys: string[] = []
  for (let i = 0; i < props.allFields.length; i++) {
    if (props.currentIndex >= 0 && i >= props.currentIndex) break
    keys.push(props.allFields[i].fieldKey)
  }
  return keys
})

watch(() => props.fieldData, (val) => {
  if (val) {
    form.value = { ...val }
    fixedValue.value = ''
    dateFormat.value = 'YYYYMMDD'
    if (val.ruleType === 'FIXED' && val.ruleConfigJson) {
      try { fixedValue.value = JSON.parse(val.ruleConfigJson).value || '' } catch { /* ignore */ }
    }
    if (val.ruleType === 'DATE' && val.ruleConfigJson) {
      try { dateFormat.value = JSON.parse(val.ruleConfigJson).format || 'YYYYMMDD' } catch { /* ignore */ }
    }
    if (val.ruleType === 'AMOUNT' && val.ruleConfigJson) {
      try { amountConfig.value = JSON.parse(val.ruleConfigJson).config || amountConfig.value } catch { /* ignore */ }
    }
    if (val.ruleType === 'RANDOM' && val.ruleConfigJson) {
      try { randomConfig.value = JSON.parse(val.ruleConfigJson) || randomConfig.value } catch { /* ignore */ }
    }
    if (val.ruleType === 'EXPRESSION' && val.ruleConfigJson) {
      try { exprConfig.value = JSON.parse(val.ruleConfigJson) || exprConfig.value } catch { /* ignore */ }
    }
    if (val.ruleType === 'ENUM' && val.ruleConfigJson) {
      try { enumDefault.value = JSON.parse(val.ruleConfigJson).useDefault || '' } catch { /* ignore */ }
    }
  } else {
    form.value = getDefaultForm()
    fixedValue.value = ''
    dateFormat.value = 'YYYYMMDD'
    amountConfig.value = { precision: 2, scale: 14, decimalMode: 'IMPLICIT', signed: false }
    randomConfig.value = { mode: 'DIGIT' }
    exprConfig.value = { func: 'CONCAT', params: [] }
    enumDefault.value = ''
  }
}, { immediate: true })

function onRuleTypeChange() {
  form.value.refFieldKey = null
  form.value.refEnumKey = null
  form.value.refSequenceKey = null
  form.value.refFileId = null
}

function handleConfirm() {
  const data = { ...form.value }
  if (data.ruleType === 'FIXED') {
    data.ruleConfigJson = JSON.stringify({ value: fixedValue.value })
  } else if (data.ruleType === 'DATE') {
    data.ruleConfigJson = JSON.stringify({ format: dateFormat.value })
  } else if (data.ruleType === 'AMOUNT') {
    data.ruleConfigJson = JSON.stringify({ type: 'AMOUNT', config: { ...amountConfig.value } })
  } else if (data.ruleType === 'RANDOM') {
    data.ruleConfigJson = JSON.stringify({ ...randomConfig.value })
  } else if (data.ruleType === 'EXPRESSION') {
    data.ruleConfigJson = JSON.stringify({ ...exprConfig.value })
  } else if (data.ruleType === 'ENUM') {
    if (form.value.refEnumKey) {
      data.ruleConfigJson = JSON.stringify({ enumKey: form.value.refEnumKey, useDefault: enumDefault.value || '0' })
    } else if (enumDefault.value && enumDefault.value.trim()) {
      data.ruleConfigJson = JSON.stringify({ useDefault: enumDefault.value })
    } else {
      data.ruleConfigJson = null
    }
  }
  emit('confirm', data)
}

function handleClose() {
  emit('update:dialogVisible', false)
}
</script>

<style scoped>
.length-hint { color: #909399; font-size: 12px; margin-left: 8px; }
</style>

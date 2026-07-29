import request from '@/utils/request'

const BASE = '/meta'

// ===================== 类型定义 =====================

/** 模型信息 */
export interface ModelInfo {
  id?: string
  name?: string
  description?: string
  status?: string
  createTime?: string
  updateTime?: string
  [key: string]: unknown
}

/** 模型查询参数 */
export interface ModelQuery {
  page?: number
  size?: number
  [key: string]: unknown
}

/** 字段定义 */
export interface FieldDefinition {
  [key: string]: unknown
}

/** 枚举项 */
export interface EnumItem {
  id?: string
  key?: string
  value?: string
  [key: string]: unknown
}

/** 引用文件信息 */
export interface RefFileInfo {
  id?: string
  fileName?: string
  createTime?: string
  [key: string]: unknown
}

/** 模板文件信息 */
export interface MetaTemplateInfo {
  fileName: string
  createTime?: string
  [key: string]: unknown
}

/** FTP配置信息 */
export interface FtpConfig {
  id?: string
  host?: string
  port?: number
  username?: string
  [key: string]: unknown
}

/** 生成任务参数 */
export interface GenerateParams {
  modelId: string
  [key: string]: unknown
}

/** 执行历史记录 */
export interface ExecutionHistory {
  taskId: string
  modelId: string
  status: string
  createTime: string
  [key: string]: unknown
}

/** 分享模型参数 */
export interface ShareModelParams {
  targetUser?: string
  [key: string]: unknown
}

/** 保存字段参数 */
export interface SaveFieldsParams {
  section?: string
  fields: FieldDefinition[]
}

// ===================== 模型管理 =====================

/** 查询模型列表 */
export function listModels(params: ModelQuery): Promise<ModelInfo[]> {
  return request({ url: `${BASE}/models`, method: 'get', params })
}

/** 创建模型 */
export function createModel(data: Record<string, unknown>): Promise<ModelInfo> {
  return request({ url: `${BASE}/models`, method: 'post', data })
}

/** 获取模型详情 */
export function getModelDetail(id: string): Promise<ModelInfo> {
  return request({ url: `${BASE}/models/${id}`, method: 'get' })
}

/** 更新模型 */
export function updateModel(id: string, data: Record<string, unknown>): Promise<ModelInfo> {
  return request({ url: `${BASE}/models/${id}`, method: 'put', data })
}

/** 删除模型 */
export function deleteModel(id: string): Promise<unknown> {
  return request({ url: `${BASE}/models/${id}`, method: 'delete' })
}

/** 发布模型 */
export function publishModel(id: string): Promise<unknown> {
  return request({ url: `${BASE}/models/${id}/publish`, method: 'post' })
}

/** 分享模型 */
export function shareModel(id: string, data: ShareModelParams): Promise<unknown> {
  return request({ url: `${BASE}/models/${id}/share`, method: 'post', data })
}

// ===================== 字段管理 =====================

/** 保存模型字段 */
export function saveFields(
  modelId: string,
  data: FieldDefinition[],
  section?: string
): Promise<unknown> {
  return request({
    url: `${BASE}/models/${modelId}/fields`,
    method: 'post',
    data,
    params: section ? { section } : undefined,
  })
}

/** 校验字段 */
export function validateFields(data: FieldDefinition[]): Promise<Record<string, unknown>> {
  return request({ url: `${BASE}/fields/validate`, method: 'post', data })
}

/** 获取求和目标字段 */
export function getSumTargets(modelId: string): Promise<string[]> {
  return request({ url: `${BASE}/fields/sum-targets/${modelId}`, method: 'get' })
}

// ===================== 枚举库管理 =====================

/** 获取枚举列表 */
export function listEnums(): Promise<EnumItem[]> {
  return request({ url: `${BASE}/enums`, method: 'get' })
}

/** 获取枚举键列表 */
export function getEnumKeys(): Promise<string[]> {
  return request({ url: `${BASE}/enums/keys`, method: 'get' })
}

/** 保存枚举 */
export function saveEnum(data: Record<string, unknown>): Promise<EnumItem> {
  return request({ url: `${BASE}/enums`, method: 'post', data })
}

/** 删除枚举 */
export function deleteEnum(id: string): Promise<unknown> {
  return request({ url: `${BASE}/enums/${id}`, method: 'delete' })
}

// ===================== 引用文件管理 =====================

/** 获取引用文件列表 */
export function listRefFiles(): Promise<RefFileInfo[]> {
  return request({ url: `${BASE}/resources`, method: 'get' })
}

/** 上传引用文件 */
export function uploadRefFile(formData: FormData): Promise<unknown> {
  return request({
    url: `${BASE}/resources/upload`,
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}

/** 定义引用文件 */
export function defineRefFile(data: Record<string, unknown>): Promise<unknown> {
  return request({ url: `${BASE}/resources/define`, method: 'post', data })
}

/** 删除引用文件 */
export function deleteRefFile(id: string): Promise<unknown> {
  return request({ url: `${BASE}/resources/${id}`, method: 'delete' })
}

/** 预览引用文件 */
export function previewRefFile(id: string, lineCount = 5): Promise<string> {
  return request({
    url: `${BASE}/resources/${id}/preview`,
    method: 'get',
    params: { lineCount },
  })
}

// ===================== 模板与导入 =====================

/** 获取模板列表 */
export function listTemplates(): Promise<MetaTemplateInfo[]> {
  return request({ url: `${BASE}/template/list`, method: 'get' })
}

/** 删除模板 */
export function deleteTemplate(fileName: string): Promise<unknown> {
  return request({
    url: `${BASE}/template/delete`,
    method: 'delete',
    params: { fileName },
  })
}

/** 获取模板下载地址 */
export function getTemplateDownloadUrl(fileName: string): string {
  return `/api/meta/template/download?fileName=${encodeURIComponent(fileName)}`
}

/** 根据关键字获取模板下载地址 */
export function getMetaTemplateDownloadByKeywordUrl(keyword: string): string {
  return `/api/meta/template/downloadByKeyword?keyword=${encodeURIComponent(keyword)}`
}

/** 获取通用模板下载地址 */
export function getGeneralTemplateDownloadUrl(): string {
  return `/api/meta/template/general/download`
}

/** 导出字段Excel */
export function exportFieldExcel(data: Record<string, unknown>): Promise<Blob> {
  return request({
    url: `${BASE}/fields/exportExcel`,
    method: 'post',
    data,
    responseType: 'blob',
  })
}

/** 解析字段Excel */
export function parseFieldExcel(formData: FormData): Promise<Record<string, unknown>> {
  return request({
    url: `${BASE}/fields/parseExcel`,
    method: 'post',
    data: formData,
  })
}

// ===================== 执行与生成 =====================

/** 预览生成 */
export function preview(modelId: string): Promise<unknown> {
  return request({ url: `${BASE}/execute/preview/${modelId}`, method: 'post' })
}

/** 执行生成 */
export function generate(data: GenerateParams): Promise<{ taskId: string }> {
  return request({ url: `${BASE}/execute/generate`, method: 'post', data })
}

/** 获取任务状态 */
export function getTaskStatus(taskId: string): Promise<{ status: string }> {
  return request({ url: `${BASE}/execute/status/${taskId}`, method: 'get' })
}

/** 获取执行历史 */
export function getHistory(modelId: string): Promise<ExecutionHistory[]> {
  return request({
    url: `${BASE}/execute/history`,
    method: 'get',
    params: { modelId },
  })
}

/** 删除实体文件 */
export function deleteEntityFile(fileId: string): Promise<unknown> {
  return request({ url: `${BASE}/execute/file/${fileId}`, method: 'delete' })
}

/** 上传到FTP */
export function uploadToFtp(data: Record<string, unknown>): Promise<unknown> {
  return request({ url: `${BASE}/execute/upload-ftp`, method: 'post', data })
}

// ===================== FTP配置管理 =====================

/** 获取FTP配置列表 */
export function listFtpConfigs(): Promise<FtpConfig[]> {
  return request({ url: `${BASE}/ftp-configs`, method: 'get' })
}

/** 保存FTP配置 */
export function saveFtpConfig(data: Record<string, unknown>): Promise<FtpConfig> {
  return request({ url: `${BASE}/ftp-configs`, method: 'post', data })
}

/** 删除FTP配置 */
export function deleteFtpConfig(id: string): Promise<unknown> {
  return request({ url: `${BASE}/ftp-configs/${id}`, method: 'delete' })
}

// ===================== 系统辅助 =====================

/** 重置序列 */
export function resetSequence(data: Record<string, unknown>): Promise<unknown> {
  return request({ url: `${BASE}/sys/sequence/reset`, method: 'post', data })
}

/** 清理临时文件 */
export function cleanTemp(): Promise<unknown> {
  return request({ url: `${BASE}/sys/clean-temp`, method: 'post' })
}

import request from '@/utils/request'

// ===================== 类型定义 =====================

/** 性能测试任务查询参数 */
export interface PerformanceTaskQuery {
  page?: number
  size?: number
  [key: string]: unknown
}

/** 性能测试任务 */
export interface PerformanceTask {
  taskId: string
  taskName: string
  status?: string
  createTime?: string
  updateTime?: string
  [key: string]: unknown
}

/** 交易项 */
export interface TransItem {
  [key: string]: unknown
}

/** 批次项 */
export interface BatchItem {
  [key: string]: unknown
}

/** 数据项 */
export interface DataItem {
  [key: string]: unknown
}

/** 场景信息 */
export interface PerformanceScene {
  sceneId: string
  sceneName: string
  [key: string]: unknown
}

/** 数据计划 */
export interface PerformanceDataPlan {
  id?: string
  [key: string]: unknown
}

/** 模板文件信息 */
export interface TemplateFileInfo {
  fileName: string
  createTime?: string
  [key: string]: unknown
}

/** 方案文档信息 */
export interface DocFileInfo {
  fileName: string
  createTime?: string
  [key: string]: unknown
}

// ===================== API 函数 =====================

/** 查询任务列表 */
export function listTasks(params: PerformanceTaskQuery): Promise<PerformanceTask[]> {
  return request({
    url: '/performance/list',
    method: 'get',
    params,
  })
}

/** 获取任务详情 */
export function getTaskDetail(taskId: string): Promise<PerformanceTask> {
  return request({
    url: '/performance/detail',
    method: 'get',
    params: { taskId },
  })
}

/** 识别任务信息 */
export function recognizeTaskInfo(data: Record<string, unknown>): Promise<PerformanceTask> {
  return request({
    url: '/performance/recognize',
    method: 'post',
    data,
  })
}

/** 保存任务 */
export function saveTask(data: Record<string, unknown>): Promise<PerformanceTask> {
  return request({
    url: '/performance/saveTask',
    method: 'post',
    data,
  })
}

/** 保存交易 */
export function saveTrans(taskId: string, data: TransItem[]): Promise<unknown> {
  return request({
    url: '/performance/saveTrans',
    method: 'post',
    params: { taskId },
    data,
  })
}

/** 保存批次 */
export function saveBatches(taskId: string, data: BatchItem[]): Promise<unknown> {
  return request({
    url: '/performance/saveBatches',
    method: 'post',
    params: { taskId },
    data,
  })
}

/** 保存数据 */
export function saveDatas(taskId: string, data: DataItem[]): Promise<unknown> {
  return request({
    url: '/performance/saveDatas',
    method: 'post',
    params: { taskId },
    data,
  })
}

/** 初始化场景 */
export function initScenes(taskId: string): Promise<unknown> {
  return request({
    url: '/performance/initScenes',
    method: 'get',
    params: { taskId },
  })
}

/** 获取场景列表 */
export function getScenes(taskId: string): Promise<PerformanceScene[]> {
  return request({
    url: '/performance/getScenes',
    method: 'get',
    params: { taskId },
  })
}

/** 获取场景详情 */
export function getSceneDetails(sceneId: string): Promise<PerformanceScene> {
  return request({
    url: '/performance/getSceneDetails',
    method: 'get',
    params: { sceneId },
  })
}

/** 保存所有场景 */
export function saveAllScenes(taskId: string, data: PerformanceScene[]): Promise<unknown> {
  return request({
    url: '/performance/saveAllScenes',
    method: 'post',
    params: { taskId },
    data,
  })
}

// ===================== Excel 解析 =====================

/** 解析交易Excel */
export function parseTranExcel(data: FormData): Promise<Record<string, unknown>> {
  return request({
    url: '/performance/tran/parseExcel',
    method: 'post',
    data,
  })
}

/** 解析批次Excel */
export function parseBatchExcel(data: FormData): Promise<Record<string, unknown>> {
  return request({
    url: '/performance/tran/parseBatchExcel',
    method: 'post',
    data,
  })
}

/** 解析数据Excel */
export function parseDataExcel(data: FormData): Promise<Record<string, unknown>> {
  return request({
    url: '/performance/tran/parseDataExcel',
    method: 'post',
    data,
  })
}

// ===================== 数据计划 =====================

/** 获取数据计划 */
export function getDataPlan(taskId: string): Promise<PerformanceDataPlan> {
  return request({
    url: '/performance/getDataPlan',
    method: 'get',
    params: { taskId },
  })
}

/** 保存数据计划 */
export function saveDataPlan(data: Record<string, unknown>): Promise<unknown> {
  return request({
    url: '/performance/saveDataPlan',
    method: 'post',
    data,
  })
}

/** 保存数据详情 */
export function saveDataDetails(taskId: string, data: DataItem[]): Promise<unknown> {
  return request({
    url: '/performance/saveDataDetails',
    method: 'post',
    params: { taskId },
    data,
  })
}

// ===================== 模板文件管理 =====================

/** 获取模板文件列表 */
export function listTemplates(): Promise<TemplateFileInfo[]> {
  return request({
    url: '/performance/file/list',
    method: 'get',
  })
}

/** 删除模板文件 */
export function deleteTemplate(fileName: string): Promise<unknown> {
  return request({
    url: '/performance/file/delete',
    method: 'delete',
    params: { fileName },
  })
}

/** 获取模板文件下载地址 */
export function getTemplateDownloadUrl(fileName: string): string {
  return `/api/performance/file/download?fileName=${encodeURIComponent(fileName)}`
}

/** 根据关键字获取模板文件下载地址 */
export function getTemplateDownloadByKeywordUrl(keyword: string): string {
  return `/api/performance/file/downloadByKeyword?keyword=${encodeURIComponent(keyword)}`
}

// ===================== 方案文档管理 =====================

/** 生成方案文档 */
export function generateDoc(taskId: string): Promise<unknown> {
  return request({
    url: '/performance/doc/generate',
    method: 'post',
    params: { taskId },
  })
}

/** 获取方案文档列表 */
export function listDocs(): Promise<DocFileInfo[]> {
  return request({
    url: '/performance/doc/list',
    method: 'get',
  })
}

/** 删除方案文档 */
export function deleteDoc(fileName: string): Promise<unknown> {
  return request({
    url: '/performance/doc/delete',
    method: 'delete',
    data: { fileName },
  })
}

/** 获取方案文档下载地址 */
export function getDocDownloadUrl(fileName: string): string {
  return `/api/performance/doc/download?fileName=${encodeURIComponent(fileName)}`
}

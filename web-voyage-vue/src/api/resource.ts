import request from '@/utils/request'

// ===================== 类型定义 =====================

/** 资源汇总项 */
export interface ResourceSummary {
  deploymentLocation: string
  systemPlatform: string
  hostCount: number
  totalCpu: number
  totalMemoryGb: number
  totalStorageGb: number
}

/** 资源明细项 */
export interface ResourceDetail {
  deploymentLocation: string
  systemPlatform: string
  partitionUsage: string
  count: number
  cpuCores: number
  memoryGb: number
  dedicatedStorageGb: number
  sanStorageGb: number
  nasStorageGb: number
}

/** 文件级资源汇总分组 */
export interface FileSummaryGroup {
  originalFileName: string
  uploadCount: number
  summary: ResourceSummary[]
}

/** 资源核查返回数据 */
export interface ResourceCheckResult {
  summaryList: ResourceSummary[]
  detailList: ResourceDetail[]
  fileSummaryList: FileSummaryGroup[]
}

/** 上传/删除操作的返回体（无 data 字段时返回完整 body） */
export interface OperationResult {
  code: number
  message: string
}

// ===================== API 函数 =====================

/**
 * 核查资源：按产品ID+批次查询
 * @param productId 产品标识
 * @param batchNo 批次
 * @param fileSource 文件来源（部署方案 / 资源申请表）
 */
export function checkResources(
  productId: string,
  batchNo: string,
  fileSource: string
): Promise<ResourceCheckResult> {
  return request.get('/resource/check', {
    params: { productId, batchNo, fileSource },
  })
}

/**
 * 获取所有产品ID+批次列表
 * @returns 产品列表，格式为 "productId|batchNo"
 */
export function getProductIds(): Promise<string[]> {
  return request.get('/resource/productIds')
}

/**
 * 上传环境资源清单 Excel 文件
 * @param productId 产品标识
 * @param batchNo 批次
 * @param file 上传的 Excel 文件
 * @param fileSource 文件来源
 */
export function uploadResource(
  productId: string,
  batchNo: string,
  file: File,
  fileSource: string
): Promise<OperationResult> {
  const formData = new FormData()
  formData.append('productId', productId)
  formData.append('batchNo', batchNo)
  formData.append('file', file)
  formData.append('fileSource', fileSource)

  return request.post('/resource/uploadResource', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

/**
 * 删除指定产品下、指定原始文件名的资源数据
 * @param productId 产品标识
 * @param batchNo 批次
 * @param originalFileName 原始文件名
 */
export function deleteResourceByFile(
  productId: string,
  batchNo: string,
  originalFileName: string
): Promise<OperationResult> {
  return request.delete('/resource/deleteByFile', {
    params: { productId, batchNo, originalFileName },
  })
}

import request from '@/utils/request'
import type { WikiNodeVO, WikiDocument } from '@/types/wiki'

/**
 * 获取完整 Wiki 目录树。
 */
export function getWikiTree(): Promise<WikiNodeVO[]> {
  return request.get('/wiki/tree')
}

/**
 * 根据 ID 获取文档详情。
 */
export function getDocDetail(id: string): Promise<WikiDocument> {
  return request.get(`/wiki/${id}`)
}

/**
 * 根据标题获取文档（双链跳转专用）。
 * 若文档不存在（404），返回 null。
 */
export function getDocByTitle(title: string): Promise<WikiDocument | null> {
  return request.get('/wiki/by-title', { params: { title } })
}

/**
 * 创建或更新文档。
 */
export function saveDoc(doc: {
  id?: string
  title: string
  content?: string
  type: number
  parentId?: string
  sortOrder?: number
}): Promise<WikiDocument> {
  return request.post('/wiki/save', doc)
}

/**
 * 删除文档（级联删除子节点）。
 */
export function deleteDoc(id: string): Promise<null> {
  return request.delete(`/wiki/${id}`)
}

/**
 * 移动节点到指定父节点和排序位置。
 */
export function moveNode(
  id: string,
  newParentId: string,
  newSortOrder: number
): Promise<null> {
  return request.put(`/wiki/${id}/move`, { newParentId, newSortOrder })
}

/**
 * 批量更新同级节点排序。
 */
export function batchUpdateSortOrder(
  items: { id: string; sortOrder: number }[]
): Promise<null> {
  return request.put('/wiki/sort-batch', { items })
}

/**
 * 获取指定文件夹的直接子节点（不递归）。
 */
export function getFolderChildren(folderId: string): Promise<WikiNodeVO[]> {
  return request.get(`/wiki/${folderId}/children`)
}

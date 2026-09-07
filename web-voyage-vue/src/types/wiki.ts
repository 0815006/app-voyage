/**
 * Wiki 树节点 VO，对应后端 WikiNodeVO。
 */
export interface WikiNodeVO {
  id: string
  title: string
  type: number // 1: 文件夹, 2: 文档
  parentId: string
  updateTime?: string
  children?: WikiNodeVO[] | null
}

/**
 * Wiki 文档实体，对应后端 WikiDocument。
 */
export interface WikiDocument {
  id?: string
  title: string
  content: string
  type: number
  parentId: string
  /** 归属操作员工号(7位，READ_ONLY，仅后端注入，前端只读展示/不上送) */
  empNo?: string
  sortOrder?: number
  createTime?: string
  updateTime?: string
}

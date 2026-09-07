/**
 * 全局身份管理 —— 7位工号切换机制。
 * 依赖 localStorage 持久化当前操作者身份，默认值为空。
 */

const EMP_NO_KEY = 'voyage_emp_no'

/**
 * 工号变更自定义事件名。
 * 任意业务页面（如 Wiki、性能测试等按人隔离的功能）可监听该事件，
 * 在工号切换后立即刷新本人数据并清理上一操作者的内存残留。
 */
export const EMP_NO_CHANGED_EVENT = 'voyage:emp-no-changed'

/**
 * 验证工号格式：必须为7位数字字符串。
 */
export function isEmpNoValid(empNo: string): boolean {
  return /^\d{7}$/.test(empNo)
}

/**
 * 获取当前登录员工号，未设置时返回空字符串。
 */
export function getCurrentEmpNo(): string {
  return localStorage.getItem(EMP_NO_KEY) || ''
}

/**
 * 广播工号变更事件（携带最新工号，供按人隔离页面刷新）。
 */
function dispatchEmpNoChanged(empNo: string): void {
  window.dispatchEvent(
    new CustomEvent<string>(EMP_NO_CHANGED_EVENT, { detail: empNo })
  )
}

/**
 * 设置当前员工号并持久化到 localStorage，随后广播工号变更事件。
 */
export function setCurrentEmpNo(empNo: string): void {
  if (!isEmpNoValid(empNo)) {
    throw new Error('工号必须为7位数字')
  }
  localStorage.setItem(EMP_NO_KEY, empNo)
  dispatchEmpNoChanged(empNo)
}

/**
 * 清除当前员工号，随后广播工号变更事件（空串表示未设置）。
 */
export function clearCurrentEmpNo(): void {
  localStorage.removeItem(EMP_NO_KEY)
  dispatchEmpNoChanged('')
}

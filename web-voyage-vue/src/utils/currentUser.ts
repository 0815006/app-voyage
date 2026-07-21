/**
 * 全局身份管理 —— 7位工号切换机制。
 * 依赖 localStorage 持久化当前操作者身份，默认值为空。
 */

const EMP_NO_KEY = 'voyage_emp_no'

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
 * 设置当前员工号并持久化到 localStorage。
 */
export function setCurrentEmpNo(empNo: string): void {
  if (!isEmpNoValid(empNo)) {
    throw new Error('工号必须为7位数字')
  }
  localStorage.setItem(EMP_NO_KEY, empNo)
}

/**
 * 清除当前员工号。
 */
export function clearCurrentEmpNo(): void {
  localStorage.removeItem(EMP_NO_KEY)
}

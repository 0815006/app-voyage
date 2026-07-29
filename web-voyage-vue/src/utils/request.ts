import axios, { type AxiosInstance, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { getCurrentEmpNo } from './currentUser'
import { ElMessage } from 'element-plus'

/**
 * Axios 封装实例：
 * 1. 自动从 currentUser 读取工号并注入 X-Emp-No 请求头
 * 2. 识别 code !== 200 并通过 ElMessage.error 提示
 */

const instance: AxiosInstance = axios.create({
  baseURL: '/api',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// 请求拦截器：注入 X-Emp-No 请求头
instance.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const empNo = getCurrentEmpNo()
    if (empNo) {
      config.headers['X-Emp-No'] = empNo
    }
    return config
  },
  (error: Error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：识别业务 code 并提取 data 字段
instance.interceptors.response.use(
  (response: AxiosResponse) => {
    const body = response.data
    // 404 由业务页面自行处理（如 Wiki 双链跳转），不弹错误提示
    if (body.code && body.code !== 200 && body.code !== 404) {
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    // 提取 Result<T> 中的 data 字段直接返回
    return body.data !== undefined ? body.data : body
  },
  (error: Error) => {
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default instance

/**
 * Axios 请求封装
 *
 * 统一处理:
 *   - 请求拦截: 自动注入 Sa-Token 到请求头
 *   - 响应拦截: 自动拆解后端 Result 包装，处理 401 跳转登录、错误提示
 */
import axios from 'axios'
import { ElMessage } from 'element-plus'

const request = axios.create({
  baseURL: import.meta.env.VITE_APP_BASE_API,
  timeout: 30000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('sa-token')
  if (token) {
    config.headers['satoken'] = token
  }
  return config
})

request.interceptors.response.use(
  response => {
    const res = response.data

    if (res.code === 401) {
      ElMessage.warning('登录状态已失效，请重新登录')
      localStorage.removeItem('sa-token')
      localStorage.removeItem('username')
      window.location.href = '/login'
      return Promise.reject(new Error(res.message))
    }

    if (res.code !== 200) {
      ElMessage.error(res.message || '系统发生未知错误')
      return Promise.reject(new Error(res.message || 'Error'))
    }

    return res.data
  },
  error => {
    ElMessage.error('网络连接失败或后端服务未启动')
    return Promise.reject(error)
  }
)

export default request
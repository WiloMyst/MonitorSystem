import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建一个独立的 axios 实例
const request = axios.create({
  // 自动根据当前环境加载对应的 API 地址
  baseURL: import.meta.env.VITE_APP_BASE_API, 
  timeout: 30000 
})

// 请求拦截器：自动在每个请求的 Header 中添加 Token
request.interceptors.request.use(config => {
  // 从 localStorage 获取 token
  const token = localStorage.getItem('sa-token') 
  if (token) { 
    // 国企规范：通常将 Token 放在 Header 的 Authorization 字段，或者按 Sa-Token 默认的 satoken 字段
    config.headers['satoken'] = token 
  }
  return config
})

// 响应拦截器：自动拆开后端的 Result 包装，处理全局错误
request.interceptors.response.use(
  response => {
    const res = response.data
    
    // 【核心修复 3】：如果是 401，说明没登录或 Token 过期
    if (res.code === 401) {
      ElMessage.warning('登录状态已失效，请重新登录')
      // 清除本地失效的残留 Token
      localStorage.removeItem('sa-token')
      localStorage.removeItem('username')
      // 强制重定向回登录页
      window.location.href = '/login'
      return Promise.reject(new Error(res.message))
    }

    // 如果是其他非 200 的报错 (比如 500)
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
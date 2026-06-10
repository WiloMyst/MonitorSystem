import request from '../utils/request'

/** 用户登录接口，返回 Sa-Token 令牌字符串 */
export const loginApi = (data: any) => {
  return request.post('/auth/login', data) as Promise<string>
}
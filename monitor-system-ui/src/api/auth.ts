import request from '../utils/request'

export const loginApi = (data: any) => {
  return request.post('/auth/login', data) as Promise<string>
}
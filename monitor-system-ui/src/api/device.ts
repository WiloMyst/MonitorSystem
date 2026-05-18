import request from '../utils/request'

export interface DeviceVO {
  deviceCode: string
  deviceType: string
  status: number
  temperature: number
  lastUpdateTime: string
}

// 定义分页返回结构
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

export const getDevicePage = (current = 1, size = 10) => {
  // 注意改成了 post，并且传了 DTO 参数
  return request.post('/device/page', { current, size }) as Promise<PageResult<DeviceVO>>
}
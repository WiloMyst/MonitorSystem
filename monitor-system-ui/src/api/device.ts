import request from '../utils/request'

/** 设备信息视图对象 */
export interface DeviceVO {
  deviceCode: string
  deviceType: string
  status: number
  temperature: number
  lastUpdateTime: string
}

/** 通用分页返回结构 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/** 分页查询设备列表 */
export const getDevicePage = (current = 1, size = 10) => {
  return request.post('/device/page', { current, size }) as Promise<PageResult<DeviceVO>>
}
import request from '../utils/request'

/**
 * 触发系统缓存刷新
 * @param promptCode 要清理的业务编码，例如 'device_rag'
 */
export const refreshPromptCache = (promptCode: string) => {
  return request.post('/prompt/refreshCache', null, {
    params: { promptCode }
  }) as Promise<string>
}
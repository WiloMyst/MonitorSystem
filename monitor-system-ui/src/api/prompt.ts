import request from '../utils/request'

/**
 * 触发系统缓存刷新
 * @param promptCode 要清理的业务编码，例如 'device_rag'
 */
export const refreshPromptCache = (promptCode: string) => {
  // 注意这里的传参方式：由于后端用的是 @RequestParam，所以前端用 params 拼在 URL 后面
  return request.post('/prompt/refreshCache', null, {
    params: { promptCode }
  }) as Promise<string>
}
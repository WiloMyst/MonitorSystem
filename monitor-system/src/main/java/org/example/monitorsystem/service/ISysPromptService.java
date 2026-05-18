package org.example.monitorsystem.service;

/**
 * 动态提示词服务接口
 */
public interface ISysPromptService {

    /**
     * 根据业务编码获取提示词模板（带缓存逻辑）
     * @param promptCode 业务编码，如 "device_rag"
     * @return 模板内容
     */
    String getPromptContentByCode(String promptCode);

    /**
     * 刷新（清理）指定提示词的缓存，实现热加载
     * @param promptCode 业务编码
     */
    void refreshPromptCache(String promptCode);
}
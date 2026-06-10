package org.example.monitorsystem.core.web;

import lombok.Data;

/**
 * 全局统一响应包装
 * 所有 API 返回值均使用此格式，确保前端可统一处理:
 *   - code: 状态码 (200=成功, 4xx=客户端错误, 5xx=服务端错误)
 *   - message: 提示信息
 *   - data: 业务数据负载
 */
@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
package org.example.monitorsystem.common;

import lombok.Data;

/**
 * 企业级全局统一返回格式
 */
@Data
public class Result<T> {
    private Integer code; // 状态码：200代表成功，500代表失败
    private String message; // 给前端的提示信息
    private T data; // 真正的数据负载

    // 成功时的快捷方法
    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("操作成功");
        result.setData(data);
        return result;
    }

    // 失败时的快捷方法
    public static <T> Result<T> error(Integer code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
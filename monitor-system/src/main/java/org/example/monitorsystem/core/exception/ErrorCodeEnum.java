package org.example.monitorsystem.core.exception;

public enum ErrorCodeEnum {
    SUCCESS(200, "操作成功"),
    PARAM_ERROR(400, "参数校验失败"),
    UNAUTHORIZED(401, "暂未登录或token已经过期"),
    FORBIDDEN(403, "没有相关权限"),
    USER_NOT_FOUND(4001, "账号不存在或已被冻结"),
    PASSWORD_ERROR(4002, "账号或密码错误"),
    SYSTEM_ERROR(500, "系统内部繁忙，请稍后再试"); // 绝不向前端暴露真实的 500 堆栈

    private final int code;
    private final String message;

    ErrorCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }
    public int getCode() { return code; }
    public String getMessage() { return message; }
}
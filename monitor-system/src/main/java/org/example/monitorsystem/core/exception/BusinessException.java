package org.example.monitorsystem.core.exception;
import lombok.Getter;

/**
 * 业务异常
 * 用于在业务逻辑中主动抛出的可预期异常，携带 ErrorCodeEnum 错误码。
 */
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCodeEnum errorCode;

    public BusinessException(ErrorCodeEnum errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }
}
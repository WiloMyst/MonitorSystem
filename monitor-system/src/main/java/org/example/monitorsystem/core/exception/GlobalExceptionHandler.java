package org.example.monitorsystem.core.exception;

import cn.dev33.satoken.exception.NotLoginException;
import org.example.monitorsystem.core.web.Result;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器
 * 按优先级拦截不同类型的异常，统一包装为 Result 响应:
 *   1. JSR-303 参数校验异常 → 400 + 校验消息
 *   2. BusinessException 业务异常 → 对应错误码 + 消息
 *   3. Sa-Token 未登录异常 → 401
 *   4. 未知系统异常 → 500（不向前端暴露堆栈）
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        String errorMsg = bindingResult.getAllErrors().get(0).getDefaultMessage();
        return Result.error(ErrorCodeEnum.PARAM_ERROR.getCode(), errorMsg);
    }

    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusinessException(BusinessException e) {
        return Result.error(e.getErrorCode().getCode(), e.getMessage());
    }

    @ExceptionHandler(NotLoginException.class)
    public Result<String> handleNotLoginException(NotLoginException e) {
        return Result.error(ErrorCodeEnum.UNAUTHORIZED.getCode(), ErrorCodeEnum.UNAUTHORIZED.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        e.printStackTrace();
        return Result.error(ErrorCodeEnum.SYSTEM_ERROR.getCode(), ErrorCodeEnum.SYSTEM_ERROR.getMessage());
    }
}
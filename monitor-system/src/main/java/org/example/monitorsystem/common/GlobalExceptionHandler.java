package org.example.monitorsystem.common;

import cn.dev33.satoken.exception.NotLoginException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 1. 专门拦截 JSR-303 参数校验报错
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        // 提取我们在 DTO 里写的 message（比如："账号绝对不能为空"）
        String errorMsg = bindingResult.getAllErrors().get(0).getDefaultMessage();
        return Result.error(ErrorCodeEnum.PARAM_ERROR.getCode(), errorMsg);
    }

    // 2. 专门拦截我们的业务异常（比如密码错误、账号冻结）
    @ExceptionHandler(BusinessException.class)
    public Result<String> handleBusinessException(BusinessException e) {
        return Result.error(e.getErrorCode().getCode(), e.getMessage());
    }

    // 3. 拦截 Sa-Token 的未登录异常
    @ExceptionHandler(NotLoginException.class)
    public Result<String> handleNotLoginException(NotLoginException e) {
        return Result.error(ErrorCodeEnum.UNAUTHORIZED.getCode(), ErrorCodeEnum.UNAUTHORIZED.getMessage());
    }

    // 4. 终极兜底：拦截所有未知的系统崩溃（空指针、SQL 报错等）
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e) {
        // 在后台必须把完整堆栈打印出来供程序员排查
        e.printStackTrace();
        // 但给前端（用户）只返回一句温柔的提示，防止泄露服务器内部结构
        return Result.error(ErrorCodeEnum.SYSTEM_ERROR.getCode(), ErrorCodeEnum.SYSTEM_ERROR.getMessage());
    }
}
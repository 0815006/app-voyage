package com.voyage.config;

import com.voyage.common.BusinessException;
import com.voyage.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器 —— 统一捕获异常并封装为 Result。
 * <p>
 * 兼容 SSE（text/event-stream）长连接场景：一旦响应已提交（流式输出已经开始），
 * 无法再把 Result 序列化回写（text/event-stream 无 JSON 消息转换器，且状态码/响应体均已定型），
 * 此时仅记录日志并返回 {@code null}，避免二次抛出 {@code HttpMessageNotWritableException} 污染日志与连接。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e, HttpServletResponse response) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        if (response.isCommitted()) {
            // SSE 等流式响应已开始输出，无法再回写 Result，静默收尾即可
            return null;
        }
        response.setStatus(e.getCode());
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e, HttpServletResponse response) {
        log.warn("参数校验失败: {}", e.getMessage());
        if (response.isCommitted()) {
            return null;
        }
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        return Result.fail(400, e.getMessage());
    }

    @ExceptionHandler(RuntimeException.class)
    public Result<Void> handleRuntimeException(RuntimeException e, HttpServletResponse response) {
        log.error("运行时异常: ", e);
        if (response.isCommitted()) {
            return null;
        }
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return Result.fail("系统内部错误，请联系管理员");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e, HttpServletResponse response) {
        log.error("未知异常: ", e);
        if (response.isCommitted()) {
            return null;
        }
        response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
        return Result.fail("系统内部错误，请联系管理员");
    }
}

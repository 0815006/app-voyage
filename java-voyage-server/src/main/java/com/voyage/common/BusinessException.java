package com.voyage.common;

import lombok.Getter;

/**
 * 业务异常类，支持携带业务错误码。
 * 由 GlobalExceptionHandler 统一捕获并封装为 Result。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }
}

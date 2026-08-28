package com.woner.linkgame.exception;

/**
 * 业务异常：由全局异常处理器转为统一 JSON 响应。
 */
public class ApiException extends RuntimeException {

    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public ApiException(String message) {
        this(400, message);
    }

    public int getStatus() {
        return status;
    }
}
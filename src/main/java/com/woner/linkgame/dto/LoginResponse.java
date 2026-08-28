package com.woner.linkgame.dto;

/**
 * 登录成功响应：userId 用于前端标识，token 用于 WebSocket 鉴权。
 */
public record LoginResponse(Long userId, String email, String token) {
}
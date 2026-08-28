package com.woner.linkgame.dto;

/**
 * 登录请求体。
 */
public record LoginRequest(String email, String password) {
}
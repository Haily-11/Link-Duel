package com.woner.linkgame.dto;

/**
 * 对外的用户信息视图（不含密码）。
 */
public record UserInfo(Long id, String email, int wins, int losses, int draws, int points) {
}
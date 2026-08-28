package com.woner.linkgame.dto;

/**
 * 结算结果（用于认输 / 断线判负等主动结束）。
 */
public record SettleOutcome(String roomId, Long winnerId, int scoreA, int scoreB, String endReason) {
}
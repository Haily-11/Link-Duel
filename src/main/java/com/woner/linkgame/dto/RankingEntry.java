package com.woner.linkgame.dto;

/**
 * 排行榜条目。
 */
public record RankingEntry(int rank, long userId, String email, int wins, int points) {
}
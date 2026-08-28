package com.woner.linkgame.dto;

/**
 * 房间权威状态快照，存于 Redis Hash 并可整体发给前端。
 */
public record RoomState(
        String roomId,
        long playerA,
        long playerB,
        int[][] board,
        int scoreA,
        int scoreB,
        String status
) {
}
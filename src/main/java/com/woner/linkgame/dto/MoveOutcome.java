package com.woner.linkgame.dto;

import java.util.List;

/**
 * 一次走子的后端判定结果。
 */
public record MoveOutcome(
        boolean valid,
        String reason,
        RoomState roomState,
        List<int[]> path,
        int[] p1,
        int[] p2,
        long eliminatedBy,
        boolean gameOver,
        Long winnerId,
        String endReason
) {
    public static MoveOutcome reject(String reason) {
        return new MoveOutcome(false, reason, null, null, null, null, 0, false, null, null);
    }
}
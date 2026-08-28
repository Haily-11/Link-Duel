package com.woner.linkgame.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 对局记录：保存在 MySQL，服务端结算时写入。
 * roomId 加唯一约束，保证同一房间不会被重复结算（幂等）。
 */
@Entity
@Table(name = "game_records", indexes = {
        @Index(name = "idx_game_records_room", columnList = "roomId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 房间唯一标识，幂等结算的依据 */
    @Column(nullable = false, unique = true, length = 64)
    private String roomId;

    @Column(nullable = false)
    private Long playerAId;

    @Column(nullable = false)
    private Long playerBId;

    /** 胜者 id；null 表示平局 */
    private Long winnerId;

    @Column(nullable = false)
    private int scoreA;

    @Column(nullable = false)
    private int scoreB;

    /** COMPLETED / FORFEIT / ABANDONED */
    @Column(nullable = false, length = 32)
    private String status;

    /** 结束原因，如 CLEAR / NO_MOVE / SURRENDER / DISCONNECT */
    @Column(nullable = false, length = 32)
    private String endReason;

    @Column(nullable = false, updatable = false)
    private LocalDateTime settledAt;

    @PrePersist
    void onCreate() {
        if (settledAt == null) {
            settledAt = LocalDateTime.now();
        }
    }
}
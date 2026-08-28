package com.woner.linkgame.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * 用户实体：保存在 MySQL，记录账号与累计战绩（胜场 / 积分）。
 */
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 128)
    private String email;

    /** BCrypt 哈希，不保存明文密码 */
    @Column(nullable = false, length = 128)
    private String passwordHash;

    @Column(nullable = false)
    private int wins = 0;

    @Column(nullable = false)
    private int losses = 0;

    @Column(nullable = false)
    private int draws = 0;

    /** 累计积分：每局按胜负结算（胜 +2、平 +1、负 0，另加对局内得分） */
    @Column(nullable = false)
    private int points = 0;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
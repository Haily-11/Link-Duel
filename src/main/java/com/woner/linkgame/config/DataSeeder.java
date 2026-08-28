package com.woner.linkgame.config;

import com.woner.linkgame.entity.User;
import com.woner.linkgame.repository.UserRepository;
import com.woner.linkgame.service.RedisKeys;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 启动时自动创建题目要求的两个可直接登录账号（幂等：存在则跳过），
 * 并确保其在排行榜 ZSET 中有条目（初始 0 分，不覆盖已有分数）。
 * 相比 data.sql，这里可以用 BCrypt 对密码做哈希，避免明文入库。
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;

    public DataSeeder(UserRepository userRepository,
                      PasswordEncoder passwordEncoder,
                      StringRedisTemplate redis) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
    }

    public static final List<String> SEED_EMAILS = List.of(
            "player_a@example.com", "player_b@example.com");

    @Override
    public void run(String... args) {
        for (String email : SEED_EMAILS) {
            seed(email, "Test123456!");
        }
    }

    private void seed(String email, String rawPassword) {
        User user = userRepository.findByEmail(email).orElseGet(() -> userRepository.save(User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .wins(0)
                .losses(0)
                .draws(0)
                .points(0)
                .build()));

        // 排行榜 NX 初始化（已有分数则不覆盖）
        String member = String.valueOf(user.getId());
        if (redis.opsForZSet().score(RedisKeys.LEADERBOARD, member) == null) {
            redis.opsForZSet().add(RedisKeys.LEADERBOARD, member, user.getPoints());
        }
        System.out.println("[seed] account ready: " + email + " (id=" + user.getId() + ")");
    }
}
package com.woner.linkgame.service;

import com.woner.linkgame.dto.LoginResponse;
import com.woner.linkgame.dto.UserInfo;
import com.woner.linkgame.entity.User;
import com.woner.linkgame.exception.ApiException;
import com.woner.linkgame.repository.UserRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * 登录与 token 鉴权。token 存 Redis（session:{token} -> userId），
 * 同时服务于 REST（/api/auth/*）与 WebSocket 连接鉴权。
 */
@Service
public class AuthService {

    private static final long SESSION_TTL_SECONDS = 2 * 3600;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       StringRedisTemplate redis) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
    }

    public LoginResponse login(String email, String password) {
        if (email == null || password == null || email.isBlank()) {
            throw new ApiException(400, "邮箱和密码不能为空");
        }
        User user = userRepository.findByEmail(email.trim().toLowerCase())
                .orElseThrow(() -> new ApiException(401, "邮箱或密码错误"));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new ApiException(401, "邮箱或密码错误");
        }
        String token = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set(RedisKeys.session(token), String.valueOf(user.getId()),
                Duration.ofSeconds(SESSION_TTL_SECONDS));
        return new LoginResponse(user.getId(), user.getEmail(), token);
    }

    /** 由 token 解析出 userId，无效返回 null。用于 WebSocket 鉴权。 */
    public Long resolveUserId(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        String v = redis.opsForValue().get(RedisKeys.session(token));
        if (v == null) {
            return null;
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 刷新 token 有效期（活跃连接续期，可选）。 */
    public void touch(String token) {
        if (token != null && !token.isBlank()) {
            redis.expire(RedisKeys.session(token), Duration.ofSeconds(SESSION_TTL_SECONDS));
        }
    }

    public UserInfo getUserInfo(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException(404, "用户不存在"));
        return new UserInfo(user.getId(), user.getEmail(),
                user.getWins(), user.getLosses(), user.getDraws(), user.getPoints());
    }
}
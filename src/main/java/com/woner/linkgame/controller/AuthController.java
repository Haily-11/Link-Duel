package com.woner.linkgame.controller;

import com.woner.linkgame.dto.LoginRequest;
import com.woner.linkgame.dto.LoginResponse;
import com.woner.linkgame.dto.UserInfo;
import com.woner.linkgame.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
        LoginResponse resp = authService.login(req.email(), req.password());
        return ResponseEntity.ok(Map.of(
                "success", true,
                "data", resp
        ));
    }

    /** 通过 token 获取当前用户信息（前端启动时校验会话是否有效） */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(@RequestParam String token) {
        Long userId = authService.resolveUserId(token);
        if (userId == null) {
            return ResponseEntity.status(401).body(Map.of("success", false, "message", "会话已失效，请重新登录"));
        }
        UserInfo info = authService.getUserInfo(userId);
        return ResponseEntity.ok(Map.of("success", true, "data", info));
    }
}
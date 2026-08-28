package com.woner.linkgame.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 通用 Bean 装配。仅引入 spring-security-crypto 的 BCrypt，
 * 不引入完整 Spring Security，避免额外的过滤器链配置。
 *
 * <p>Spring Boot 4 的 webmvc starter 不再自动装配 ObjectMapper，
 * 这里显式提供，供 BoardService 等序列化棋盘使用。</p>
 */
@Configuration
public class AppBeans {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
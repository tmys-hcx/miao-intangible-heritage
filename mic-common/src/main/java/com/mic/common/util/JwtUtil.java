package com.mic.common.util;

import cn.hutool.jwt.JWT;
import cn.hutool.jwt.JWTValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * JWT生成、解析、校验工具
 * 说明：网关与各业务服务共用同一密钥（mic.jwt.secret），网关校验通过后向下游透传用户信息
 */
@Component
public class JwtUtil {

    /** 签名密钥（通过环境变量 JWT_SECRET 或 mic.jwt.secret 配置覆盖，各服务须保持一致；生产环境务必替换默认占位值） */
    @Value("${mic.jwt.secret:${JWT_SECRET:CHANGE_ME_MY_JWT_SECRET_KEY}}")
    private String secret;

    /** 令牌有效期（秒），默认24小时 */
    @Value("${mic.jwt.expire-seconds:86400}")
    private Long expireSeconds;

    /**
     * 生成token：携带 userId、account、role
     */
    public String createToken(Long userId, String account, Integer role) {
        long nowSeconds = System.currentTimeMillis() / 1000;
        return JWT.create()
                .setPayload("userId", String.valueOf(userId))
                .setPayload("account", account)
                .setPayload("role", role)
                .setPayload("iat", nowSeconds)
                .setPayload("exp", nowSeconds + expireSeconds)
                .setKey(secret.getBytes(StandardCharsets.UTF_8))
                .sign();
    }

    /**
     * 校验token：签名正确且未过期
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            JWT jwt = parseToken(token);
            if (!jwt.verify()) {
                return false;
            }
            // 校验 exp 过期时间（过期抛出 ValidateException）
            JWTValidator.of(jwt).validateDate();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 解析token中的用户ID */
    public Long getUserId(String token) {
        return toLong(getPayload(token, "userId"));
    }

    /** 解析token中的账号 */
    public String getAccount(String token) {
        Object value = getPayload(token, "account");
        return value == null ? null : value.toString();
    }

    /** 解析token中的角色 */
    public Integer getRole(String token) {
        return toInteger(getPayload(token, "role"));
    }

    private JWT parseToken(String token) {
        return JWT.of(token).setKey(secret.getBytes(StandardCharsets.UTF_8));
    }

    private Object getPayload(String token, String name) {
        return parseToken(token).getPayload(name);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }

    private Integer toInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(value.toString());
    }
}

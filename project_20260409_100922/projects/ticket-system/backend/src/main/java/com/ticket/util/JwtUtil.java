package com.ticket.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类
 */
@Component
public class JwtUtil {

    /**
     * JWT 密钥（从配置文件读取，默认值）
     */
    @Value("${jwt.secret:ticket-system-secret-key-2024}")
    private String secret;

    /**
     * JWT 过期时间（7天）
     */
    @Value("${jwt.expiration:604800000}")
    private Long expiration;

    /**
     * 生成 Token
     */
    public String generateToken(Long userId, String phone) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("phone", phone);
        return generateToken(claims);
    }

    /**
     * 生成 Token（带自定义声明）
     */
    public String generateToken(Map<String, Object> claims) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .claims(claims)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    /**
     * 从 Token 中获取用户ID
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims != null) {
            Object userId = claims.get("userId");
            if (userId instanceof Integer) {
                return ((Integer) userId).longValue();
            }
            return (Long) userId;
        }
        return null;
    }

    /**
     * 从 Token 中获取手机号
     */
    public String getPhoneFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.get("phone", String.class) : null;
    }

    /**
     * 验证 Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            getClaimsFromToken(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 判断 Token 是否过期
     */
    public boolean isTokenExpired(String token) {
        Date expiration = getExpirationFromToken(token);
        return expiration != null && expiration.before(new Date());
    }

    /**
     * 从 Token 中获取过期时间
     */
    private Date getExpirationFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.getExpiration() : null;
    }

    /**
     * 从 Token 中获取 Claims
     */
    private Claims getClaimsFromToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 生成带角色信息的 Token
     */
    public String generateToken(Long userId, String phone, Long roleId, String roleName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("phone", phone);
        claims.put("roleId", roleId);
        claims.put("roleName", roleName);
        return generateToken(claims);
    }

    /**
     * 从 Token 中获取角色ID
     */
    public Long getRoleIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims != null) {
            Object roleId = claims.get("roleId");
            if (roleId == null) {
                return null;
            }
            if (roleId instanceof Integer) {
                return ((Integer) roleId).longValue();
            }
            return (Long) roleId;
        }
        return null;
    }

    /**
     * 从 Token 中获取角色名称
     */
    public String getRoleNameFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.get("roleName", String.class) : null;
    }
}

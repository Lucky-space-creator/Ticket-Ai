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
    @Value("${jwt.secret:ticket-system-secret-key-2024-please-change-in-production}")
    private String secret;

    /**
     * JWT 过期时间（7天）
     */
    @Value("${jwt.expiration:604800000}")
    private Long expiration;

    /**
     * 用户类型：普通用户
     */
    public static final String USER_TYPE_USER = "user";

    /**
     * 用户类型：员工
     */
    public static final String USER_TYPE_EMPLOYEE = "employee";

    /**
     * 管理员角色
     */
    public static final String ROLE_ADMIN = "admin";

    /**
     * 客服角色
     */
    public static final String ROLE_CUSTOMER_SERVICE = "customer_service";

    /**
     * 普通用户角色
     */
    public static final String ROLE_USER = "user";

    /**
     * 默认JWT过期时间（7天，毫秒）
     */
    public static final long DEFAULT_EXPIRATION = 7 * 24 * 60 * 60 * 1000L;

    /**
     * JWT token前缀
     */
    public static final String TOKEN_PREFIX = "Bearer ";

    /**
     * JWT header名称
     */
    public static final String HEADER_NAME = "Authorization";

    /**
     * 用户ID字段名
     */
    public static final String CLAIM_USER_ID = "userId";

    /**
     * 员工ID字段名
     */
    public static final String CLAIM_EMPLOYEE_ID = "employeeId";

    /**
     * 用户类型字段名
     */
    public static final String CLAIM_USER_TYPE = "userType";

    /**
     * 手机号字段名
     */
    public static final String CLAIM_PHONE = "phone";

    /**
     * 角色字段名
     */
    public static final String CLAIM_ROLES = "roles";

    /**
     * 权限字段名
     */
    public static final String CLAIM_PERMISSIONS = "permissions";

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

    /**
     * 生成员工令牌
     */
    public String generateEmployeeToken(Long employeeId, String phone, String employeeNo, String name) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("employeeId", employeeId);
        claims.put("phone", phone);
        claims.put("employeeNo", employeeNo);
        claims.put("name", name);
        claims.put("userType", USER_TYPE_EMPLOYEE);
        return generateToken(claims);
    }

    /**
     * 生成带角色信息的员工令牌
     */
    public String generateEmployeeTokenWithRole(Long employeeId, String phone, String employeeNo, String name, Long roleId, String roleName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("employeeId", employeeId);
        claims.put("phone", phone);
        claims.put("employeeNo", employeeNo);
        claims.put("name", name);
        claims.put("userType", USER_TYPE_EMPLOYEE);
        claims.put("roleId", roleId);
        claims.put("roleName", roleName);
        return generateToken(claims);
    }

    /**
     * 从令牌中获取用户类型
     */
    public String getUserTypeFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims != null ? claims.get("userType", String.class) : null;
    }

    /**
     * 从令牌中获取员工ID
     */
    public Long getEmployeeIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        if (claims != null) {
            Object employeeId = claims.get("employeeId");
            if (employeeId instanceof Integer) {
                return ((Integer) employeeId).longValue();
            }
            return (Long) employeeId;
        }
        return null;
    }
}
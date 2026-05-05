package com.ticket.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 加密工具类
 */
public class CryptoUtil {

    /**
     * BCrypt 加密器
     */
    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * BCrypt 密码前缀
     */
    private static final String BCRYPT_PREFIX = "$2";

    /**
     * 加密密码
     */
    public static String encryptPassword(String password) {
        return encoder.encode(password);
    }

    /**
     * 验证密码（兼容旧版明文密码）
     * @param rawPassword 明文密码
     * @param encodedPassword 存储的密码（可能是 BCrypt 加密，也可能是明文）
     * @return 验证结果
     */
    public static boolean verifyPassword(String rawPassword, String encodedPassword) {
        if (encodedPassword == null || rawPassword == null) {
            return false;
        }

        // 如果存储的密码是 BCrypt 格式，使用 BCrypt 验证
        if (isBcryptPassword(encodedPassword)) {
            return encoder.matches(rawPassword, encodedPassword);
        }

        // 如果不是 BCrypt 格式（可能是旧版的明文密码），直接比较
        // 注意：这是为了兼容旧数据，验证通过后会自动升级为 BCrypt
        return rawPassword.equals(encodedPassword);
    }

    /**
     * 判断密码是否为 BCrypt 格式
     * @param password 密码
     * @return 是否为 BCrypt 格式
     */
    public static boolean isBcryptPassword(String password) {
        return password != null && password.startsWith(BCRYPT_PREFIX);
    }

    /**
     * 简单加密（用于身份证号等敏感信息）
     * 使用简单的可逆加密，仅用于演示
     */
    public static String encrypt(String data) {
        if (data == null || data.isEmpty()) {
            return data;
        }
        // 使用 Base64 简单编码（生产环境应使用 AES 等强加密）
        return Base64.getEncoder().encodeToString(
            data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * 解密
     */
    public static String decrypt(String encryptedData) {
        if (encryptedData == null || encryptedData.isEmpty()) {
            return encryptedData;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedData);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    // ... existing code ...

    public static void main(String[] args) {
        String password = "110101198405055699";

        // 生成新的哈希
        String hashed = CryptoUtil.encrypt(password);
        System.out.println("=== 新生成的哈希 ===");
        System.out.println("加密密码: " + hashed);
        System.out.println("长度: " + hashed.length());

        // 测试新哈希的验证
        boolean valid = CryptoUtil.verifyPassword(password, hashed);
        System.out.println("新哈希验证结果: " + valid);

        // 测试数据库中的哈希
        String dbHash = "$2a$10$vI8aWBnW3fID.ZQ4/zo1G.q1lRps.9cGLcZEiGDMVr5yUP1KUOYTa";
        System.out.println("\n=== 数据库中的哈希 ===");
        System.out.println("数据库哈希: " + dbHash);
        System.out.println("数据库哈希长度: " + dbHash.length());
        System.out.println("是否为BCrypt格式: " + CryptoUtil.isBcryptPassword(dbHash));

        boolean dbValid = CryptoUtil.verifyPassword(password, dbHash);
        System.out.println("数据库哈希验证结果: " + dbValid);

        // 如果验证失败，尝试逐个字符比较
        if (!dbValid) {
            System.out.println("\n=== 详细分析 ===");
            System.out.println("预期前缀: $2a$10$");
            System.out.println("实际前缀: " + dbHash.substring(0, Math.min(7, dbHash.length())));

            // 检查每个字符
            for (int i = 0; i < dbHash.length(); i++) {
                System.out.print(i + ":" + dbHash.charAt(i) + " ");
                if ((i + 1) % 10 == 0) System.out.println();
            }
            System.out.println();
        }

        // 测试常见错误
        System.out.println("\n=== 测试其他可能的哈希 ===");
        String testHash1 = "$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH";
        System.out.println("旧哈希验证: " + CryptoUtil.verifyPassword(password, testHash1));
        System.out.println("旧哈希长度: " + testHash1.length());
    }
}
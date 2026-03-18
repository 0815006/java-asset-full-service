package com.asset.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
public class TokenUtils {

    @Value("${auth.secret-key}")
    private String secretKey;

    @Value("${auth.token-expiration-hours}")
    private int expirationHours;

    /**
     * 生成 Token: userId:timestamp:signature
     */
    public String generateToken(Long userId) {
        long timestamp = System.currentTimeMillis();
        String payload = userId + ":" + timestamp;
        String signature = sign(payload);
        return Base64.getEncoder().encodeToString((payload + ":" + signature).getBytes());
    }

    /**
     * 校验 Token
     */
    public Long verifyToken(String token) {
        try {
            String decoded = new String(Base64.getDecoder().decode(token));
            String[] parts = decoded.split(":");
            if (parts.length != 3) return null;

            Long userId = Long.parseLong(parts[0]);
            long timestamp = Long.parseLong(parts[1]);
            String signature = parts[2];

            // 1. 校验签名
            if (!signature.equals(sign(parts[0] + ":" + parts[1]))) {
                return null;
            }

            // 2. 校验时效
            long now = System.currentTimeMillis();
            if (now - timestamp > (long) expirationHours * 60 * 60 * 1000) {
                return null; // 已过期
            }

            return userId;
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        // 简单的签名逻辑，实际生产应使用 HMAC-SHA256
        return Integer.toHexString((data + secretKey).hashCode());
    }
}

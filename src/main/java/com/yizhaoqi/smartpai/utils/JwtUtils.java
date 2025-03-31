package com.yizhaoqi.smartpai.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${jwt.secret-key}")
    private String secretKeyBase64; // 这里存的是 Base64 编码后的密钥

    private static final long EXPIRATION_TIME = 86400000; // 24 hours

    /**
     * 解析 Base64 密钥，并返回 SecretKey
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Base64.getDecoder().decode(secretKeyBase64);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT Token
     */
    public String generateToken(String username) {
        SecretKey key = getSigningKey(); // 解析密钥

        return Jwts.builder()
                .setSubject(username)
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 验证 JWT Token 是否有效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token);

            logger.info("Token validation successful");
            return true;
        } catch (ExpiredJwtException e) {
            logger.warn("Token expired: {}", token, e);
        } catch (SignatureException e) {
            logger.warn("Invalid signature: {}", token, e);
        } catch (Exception e) {
            logger.error("Error validating token: {}", token, e);
        }
        return false;
    }

    /**
     * 从 JWT Token 中提取用户名
     */
    public String extractUsernameFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey()) // 使用正确的密钥
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.getSubject();
        } catch (Exception e) {
            logger.error("Error extracting username from token: {}", token, e);
            return null;
        }
    }
}

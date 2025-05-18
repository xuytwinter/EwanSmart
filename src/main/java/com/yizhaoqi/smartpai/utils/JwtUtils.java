package com.yizhaoqi.smartpai.utils;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtils {
    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${jwt.secret-key}")
    private String secretKeyBase64; // 这里存的是 Base64 编码后的密钥

    private static final long EXPIRATION_TIME = 86400000; // 24 hours
    
    @Autowired
    private UserRepository userRepository;

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
        
        // 获取用户信息
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // 创建token内容
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getRole().name());
        claims.put("userId", user.getId().toString()); // 添加用户ID到JWT
        
        // 添加组织标签信息
        if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
            claims.put("orgTags", user.getOrgTags());
        }
        
        // 添加主组织标签信息
        if (user.getPrimaryOrg() != null && !user.getPrimaryOrg().isEmpty()) {
            claims.put("primaryOrg", user.getPrimaryOrg());
        }

        return Jwts.builder()
                .setClaims(claims)
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
    
    /**
     * 从 JWT Token 中提取用户ID
     */
    public String extractUserIdFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.get("userId", String.class);
        } catch (Exception e) {
            logger.error("Error extracting userId from token: {}", token, e);
            return null;
        }
    }
    
    /**
     * 从 JWT Token 中提取用户角色
     */
    public String extractRoleFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.get("role", String.class);
        } catch (Exception e) {
            logger.error("Error extracting role from token: {}", token, e);
            return null;
        }
    }
    
    /**
     * 从 JWT Token 中提取组织标签
     */
    public String extractOrgTagsFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.get("orgTags", String.class);
        } catch (Exception e) {
            logger.error("Error extracting organization tags from token: {}", token, e);
            return null;
        }
    }
    
    /**
     * 从 JWT Token 中提取主组织标签
     */
    public String extractPrimaryOrgFromToken(String token) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            return claims.get("primaryOrg", String.class);
        } catch (Exception e) {
            logger.error("Error extracting primary organization from token: {}", token, e);
            return null;
        }
    }
}

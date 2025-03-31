package com.yizhaoqi.smartpai.config;

import com.yizhaoqi.smartpai.service.CustomUserDetailsService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 自定义的过滤器，用于解析请求头中的 JWT Token，并验证用户身份。
 * 如果 Token 有效，则将用户信息和权限设置到 Spring Security 的上下文中，后续的请求可以基于用户角色进行授权。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtils jwtUtils; // 用于生成和解析 JWT Token

    @Autowired
    private CustomUserDetailsService userDetailsService; // 加载用户详细信息

    /**
     * 每次请求都会调用此方法，用于解析 JWT Token 并设置用户认证信息。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            // 从请求头中提取 JWT Token
            String token = extractToken(request);
            if (token != null && jwtUtils.validateToken(token)) { // 验证 Token 是否有效
                String username = jwtUtils.extractUsernameFromToken(token); // 从 Token 中提取用户名
                UserDetails userDetails = userDetailsService.loadUserByUsername(username); // 加载用户详细信息

                // 创建认证对象并设置到 Security 上下文中
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (Exception e) {
            // 记录错误日志
            logger.error("Cannot set user authentication: {}", e);
        }
        filterChain.doFilter(request, response); // 继续执行过滤链
    }

    /**
     * 从请求头中提取 JWT Token。
     */
    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7); // 去掉 "Bearer " 前缀
        }
        return null;
    }
}

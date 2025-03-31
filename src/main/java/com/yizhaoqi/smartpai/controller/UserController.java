package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.service.UserService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Autowired
    private UserService userService;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    // 用户注册接口
    // 接收用户请求体中的用户名和密码，并调用用户服务进行注册
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody UserRequest request) {
        try {
            if (request.username() == null || request.username().isEmpty() ||
                    request.password() == null || request.password().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Username and password cannot be empty"));
            }
            userService.registerUser(request.username(), request.password());
            return ResponseEntity.ok(Map.of("code", 200, "message", "User registered successfully"));
        } catch (CustomException e) {
            // 记录完整的异常信息
            logger.error("Register failed: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of(e.getStatus().value(), e.getMessage()));
        } catch (Exception e) {
            // 记录完整的异常信息
            logger.error("Register failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(500, "Internal server error"));
        }
    }

    // 用户登录接口
    // 验证用户身份并生成JWT令牌
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserRequest request) {
        try {
            if (request.username() == null || request.username().isEmpty() ||
                    request.password() == null || request.password().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Username and password cannot be empty"));
            }
            String username = userService.authenticateUser(request.username(), request.password());
            if (username == null) {
                return ResponseEntity.status(401).body(Map.of("code", 401, "message", "Invalid credentials"));
            }
            String token = jwtUtils.generateToken(username);
            return ResponseEntity.ok(Map.of("code", 200, "message", "Login successful", "data", Map.of("token", token)));
        } catch (CustomException e) {
            // 记录完整的异常信息
            logger.error("Register failed: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of(e.getStatus().value(), e.getMessage()));
        } catch (Exception e) {
            // 记录异常日志
            logger.error("Login failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String token) {

        String username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        if (username == null || username.isEmpty()) {
            throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));

        // 手动构建返回对象，不包含 password 字段
        Map<String, Object> displayUserData = new LinkedHashMap<>();
        displayUserData.put("id", user.getId());
        displayUserData.put("username", user.getUsername());
        displayUserData.put("role", user.getRole());
        displayUserData.put("createdAt", user.getCreatedAt());
        displayUserData.put("updatedAt", user.getUpdatedAt());

        // 返回响应
        return ResponseEntity.ok(Map.of("code", 200, "message", "Get user detail successful", "data", displayUserData));
    }
}

// 记录类用于封装用户请求中的用户名和密码
record UserRequest(String username, String password) {}

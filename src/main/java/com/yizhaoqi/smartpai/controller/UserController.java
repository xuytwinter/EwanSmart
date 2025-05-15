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

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            // 记录完整的异常信息
            logger.error("Register failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
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
            logger.error("Login failed: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            // 记录异常日志
            logger.error("Login failed: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    // 获取当前用户信息
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String token) {
        try {
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
            
            // 添加组织标签信息
            if (user.getOrgTags() != null && !user.getOrgTags().isEmpty()) {
                List<String> orgTagsList = Arrays.asList(user.getOrgTags().split(","));
                displayUserData.put("orgTags", orgTagsList);
            } else {
                displayUserData.put("orgTags", List.of());
            }
            
            // 添加主组织标签信息
            displayUserData.put("primaryOrg", user.getPrimaryOrg());
            
            displayUserData.put("createdAt", user.getCreatedAt());
            displayUserData.put("updatedAt", user.getUpdatedAt());

            // 返回响应
            return ResponseEntity.ok(Map.of("code", 200, "message", "Get user detail successful", "data", displayUserData));
        } catch (CustomException e) {
            logger.error("Get user detail failed: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Get user detail failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }
    
    // 获取用户组织标签信息
    @GetMapping("/org-tags")
    public ResponseEntity<?> getUserOrgTags(@RequestHeader("Authorization") String token) {
        try {
            String username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }
            
            Map<String, Object> orgTagsInfo = userService.getUserOrgTags(username);
            
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "Get user organization tags successful", 
                "data", orgTagsInfo
            ));
        } catch (CustomException e) {
            logger.error("Get user organization tags failed: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Get user organization tags failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }
    
    // 设置用户主组织标签
    @PutMapping("/primary-org")
    public ResponseEntity<?> setPrimaryOrg(@RequestHeader("Authorization") String token, @RequestBody PrimaryOrgRequest request) {
        try {
            String username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
            }
            
            if (request.primaryOrg() == null || request.primaryOrg().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("code", 400, "message", "Primary organization tag cannot be empty"));
            }
            
            userService.setUserPrimaryOrg(username, request.primaryOrg());
            
            return ResponseEntity.ok(Map.of("code", 200, "message", "Primary organization set successfully"));
        } catch (CustomException e) {
            logger.error("Set primary organization failed: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Set primary organization failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "Internal server error"));
        }
    }

    // 获取当前用户组织标签信息 (供上传文件时使用)
    @GetMapping("/upload-orgs")
    public ResponseEntity<?> getUploadOrgTags(@RequestAttribute("userId") String userId) {
        try {
            logger.info("获取用户上传组织标签信息: userId={}", userId);
            
            // 获取用户所有组织标签
            List<String> orgTags = Arrays.asList(userService.getUserOrgTags(userId).get("orgTags").toString().split(","));
            // 获取用户主组织标签
            String primaryOrg = userService.getUserPrimaryOrg(userId);
            
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("orgTags", orgTags);
            responseData.put("primaryOrg", primaryOrg);
            
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "获取用户上传组织标签成功", 
                "data", responseData
            ));
        } catch (Exception e) {
            logger.error("获取用户上传组织标签失败: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "code", 500, 
                "message", "获取用户上传组织标签失败: " + e.getMessage()
            ));
        }
    }
}

// 用户请求记录类
record UserRequest(String username, String password) {}

// 主组织标签请求记录类
record PrimaryOrgRequest(String primaryOrg) {}

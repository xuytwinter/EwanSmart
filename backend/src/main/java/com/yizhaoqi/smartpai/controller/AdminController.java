package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.OrganizationTag;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.OrganizationTagRepository;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.service.UserService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理员控制器，提供管理知识库、查看系统状态和监控用户活动的接口
 */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private static final Logger logger = LoggerFactory.getLogger(AdminController.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserService userService;
    
    @Autowired
    private OrganizationTagRepository organizationTagRepository;

    /**
     * 获取所有用户列表
     */
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        User admin = validateAdmin(adminUsername);
        
        try {
            List<User> users = userRepository.findAll();
            // 移除敏感信息
            users.forEach(user -> user.setPassword(null));
            return ResponseEntity.ok(Map.of("code", 200, "message", "Get all users successful", "data", users));
        } catch (Exception e) {
            logger.error("Failed to get all users: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "Failed to get users: " + e.getMessage()));
        }
    }

    /**
     * 添加知识库文档
     */
    @PostMapping("/knowledge/add")
    public ResponseEntity<?> addKnowledgeDocument(
            @RequestHeader("Authorization") String token,
            @RequestParam("file") MultipartFile file,
            @RequestParam("description") String description) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            // 这里应该调用知识库管理服务来处理文档
            // knowledgeService.addDocument(file, description);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "文档已成功添加到知识库");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to add document to knowledge base: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "添加文档失败: " + e.getMessage()));
        }
    }

    /**
     * 删除知识库文档
     */
    @DeleteMapping("/knowledge/{documentId}")
    public ResponseEntity<?> deleteKnowledgeDocument(
            @RequestHeader("Authorization") String token,
            @PathVariable("documentId") String documentId) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            // 这里应该调用知识库管理服务来删除文档
            // knowledgeService.deleteDocument(documentId);
            
            Map<String, String> response = new HashMap<>();
            response.put("message", "文档已成功从知识库中删除");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Failed to delete document from knowledge base: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "删除文档失败: " + e.getMessage()));
        }
    }

    /**
     * 获取系统状态
     */
    @GetMapping("/system/status")
    public ResponseEntity<?> getSystemStatus(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            // 这里应该调用系统监控服务来获取系统状态
            // SystemStatus status = monitoringService.getSystemStatus();
            
            // 模拟系统状态数据
            Map<String, Object> status = new HashMap<>();
            status.put("cpu_usage", "30%");
            status.put("memory_usage", "45%");
            status.put("disk_usage", "60%");
            status.put("active_users", 15);
            status.put("total_documents", 250);
            status.put("total_conversations", 1200);
            
            return ResponseEntity.ok(Map.of("data", status));
        } catch (Exception e) {
            logger.error("Failed to get system status: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取系统状态失败: " + e.getMessage()));
        }
    }

    /**
     * 获取用户活动日志
     */
    @GetMapping("/user-activities")
    public ResponseEntity<?> getUserActivities(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            // 这里应该调用用户活动监控服务来获取活动日志
            // List<UserActivity> activities = activityService.getUserActivities(username, startDate, endDate);
            
            // 模拟用户活动数据
            List<Map<String, Object>> activities = List.of(
                Map.of(
                    "username", "user1",
                    "action", "LOGIN",
                    "timestamp", "2023-03-01T10:15:30",
                    "ip_address", "192.168.1.100"
                ),
                Map.of(
                    "username", "user2",
                    "action", "UPLOAD_FILE",
                    "timestamp", "2023-03-01T11:20:45",
                    "ip_address", "192.168.1.101"
                )
            );
            
            return ResponseEntity.ok(Map.of("data", activities));
        } catch (Exception e) {
            logger.error("Failed to get user activities: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "获取用户活动失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建管理员用户
     */
    @PostMapping("/users/create-admin")
    public ResponseEntity<?> createAdminUser(
            @RequestHeader("Authorization") String token,
            @RequestBody AdminUserRequest request) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            userService.createAdminUser(request.username(), request.password(), adminUsername);
            return ResponseEntity.ok(Map.of("code", 200, "message", "管理员用户创建成功"));
        } catch (CustomException e) {
            logger.error("Failed to create admin user: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to create admin user: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "创建管理员用户失败: " + e.getMessage()));
        }
    }
    
    /**
     * 创建组织标签
     */
    @PostMapping("/org-tags")
    public ResponseEntity<?> createOrganizationTag(
            @RequestHeader("Authorization") String token,
            @RequestBody OrgTagRequest request) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            OrganizationTag tag = userService.createOrganizationTag(
                request.tagId(), 
                request.name(), 
                request.description(), 
                request.parentTag(), 
                adminUsername
            );
            return ResponseEntity.ok(Map.of("code", 200, "message", "组织标签创建成功", "data", tag));
        } catch (CustomException e) {
            logger.error("Failed to create organization tag: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to create organization tag: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "创建组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取所有组织标签
     */
    @GetMapping("/org-tags")
    public ResponseEntity<?> getAllOrganizationTags(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            List<OrganizationTag> tags = organizationTagRepository.findAll();
            return ResponseEntity.ok(Map.of("code", 200, "message", "获取组织标签成功", "data", tags));
        } catch (Exception e) {
            logger.error("Failed to get organization tags: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 为用户分配组织标签
     */
    @PutMapping("/users/{userId}/org-tags")
    public ResponseEntity<?> assignOrgTagsToUser(
            @RequestHeader("Authorization") String token,
            @PathVariable Long userId,
            @RequestBody AssignOrgTagsRequest request) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            userService.assignOrgTagsToUser(userId, request.orgTags(), adminUsername);
            return ResponseEntity.ok(Map.of("code", 200, "message", "组织标签分配成功"));
        } catch (CustomException e) {
            logger.error("Failed to assign organization tags: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to assign organization tags: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "分配组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取组织标签树结构
     */
    @GetMapping("/org-tags/tree")
    public ResponseEntity<?> getOrganizationTagTree(@RequestHeader("Authorization") String token) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            List<Map<String, Object>> tagTree = userService.getOrganizationTagTree();
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "获取组织标签树成功", 
                "data", tagTree
            ));
        } catch (Exception e) {
            logger.error("Failed to get organization tag tree: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取组织标签树失败: " + e.getMessage()));
        }
    }
    
    /**
     * 更新组织标签
     */
    @PutMapping("/org-tags/{tagId}")
    public ResponseEntity<?> updateOrganizationTag(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId,
            @RequestBody OrgTagUpdateRequest request) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            OrganizationTag updatedTag = userService.updateOrganizationTag(
                tagId, 
                request.name(), 
                request.description(), 
                request.parentTag(), 
                adminUsername
            );
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "组织标签更新成功", 
                "data", updatedTag
            ));
        } catch (CustomException e) {
            logger.error("Failed to update organization tag: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to update organization tag: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "更新组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 删除组织标签
     */
    @DeleteMapping("/org-tags/{tagId}")
    public ResponseEntity<?> deleteOrganizationTag(
            @RequestHeader("Authorization") String token,
            @PathVariable String tagId) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            userService.deleteOrganizationTag(tagId, adminUsername);
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "组织标签删除成功"
            ));
        } catch (CustomException e) {
            logger.error("Failed to delete organization tag: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to delete organization tag: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "删除组织标签失败: " + e.getMessage()));
        }
    }
    
    /**
     * 获取用户列表
     */
    @GetMapping("/users/list")
    public ResponseEntity<?> getUserList(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String orgTag,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        validateAdmin(adminUsername);
        
        try {
            Map<String, Object> usersData = userService.getUserList(keyword, orgTag, status, page, size);
            return ResponseEntity.ok(Map.of(
                "code", 200, 
                "message", "获取用户列表成功", 
                "data", usersData
            ));
        } catch (CustomException e) {
            logger.error("Failed to get user list: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("Failed to get user list: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("code", 500, "message", "获取用户列表失败: " + e.getMessage()));
        }
    }
    
    /**
     * 验证用户是否为管理员
     */
    private User validateAdmin(String username) {
        if (username == null || username.isEmpty()) {
            throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
        }
        
        User admin = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException("User not found", HttpStatus.NOT_FOUND));
        
        if (admin.getRole() != User.Role.ADMIN) {
            throw new CustomException("Unauthorized access: Admin role required", HttpStatus.FORBIDDEN);
        }
        
        return admin;
    }
}

/**
 * 管理员用户请求体
 */
record AdminUserRequest(String username, String password) {}

/**
 * 组织标签请求体
 */
record OrgTagRequest(String tagId, String name, String description, String parentTag) {}

/**
 * 分配组织标签请求体
 */
record AssignOrgTagsRequest(List<String> orgTags) {}

// 添加组织标签更新请求记录类
record OrgTagUpdateRequest(String name, String description, String parentTag) {} 
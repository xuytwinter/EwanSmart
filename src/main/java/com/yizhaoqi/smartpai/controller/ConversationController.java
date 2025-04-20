package com.yizhaoqi.smartpai.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.User;
import com.yizhaoqi.smartpai.repository.UserRepository;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/conversations")
public class ConversationController {
    private static final Logger logger = LoggerFactory.getLogger(ConversationController.class);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtils jwtUtils;
    
    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 查询对话历史，从Redis中获取
     */
    @GetMapping
    public ResponseEntity<?> getConversations(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date) {
        try {
            // 从token中提取用户名
            String username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            if (username == null || username.isEmpty()) {
                throw new CustomException("无效的token", HttpStatus.UNAUTHORIZED);
            }
            
            logger.info("开始查询用户 {} 的对话历史", username);
            
            // 获取用户信息
            User user = userRepository.findByUsername(username)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));
            
            // 尝试不同格式的用户ID来查询Redis
            List<String> possibleUserIds = new ArrayList<>();
            possibleUserIds.add(user.getId().toString());    // 数据库ID（Long转String）
            possibleUserIds.add(username);                 // 用户名
            possibleUserIds.add(String.valueOf(user.getId())); // 另一种数据库ID格式
            
            // 检查所有Redis键，尝试找到与用户相关的会话ID
            List<String> matchingKeys = new ArrayList<>();
            for (String uId : possibleUserIds) {
                String key = "user:" + uId + ":current_conversation";
                String conversationId = redisTemplate.opsForValue().get(key);
                if (conversationId != null) {
                    matchingKeys.add(key);
                    return getConversationsFromRedis(conversationId, username);
                }
                
                logger.debug("尝试查找Redis键: {}, 结果: {}", key, conversationId != null ? "找到" : "未找到");
            }
            
            // 无法找到任何对话记录
            logger.info("用户 {} 没有找到对话历史，尝试过的键: {}", username, possibleUserIds);
            return ResponseEntity.ok().body(Map.of("data", new ArrayList<>()));
            
        } catch (CustomException e) {
            logger.error("获取对话历史失败: {}", e.getMessage(), e);
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("获取对话历史失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "服务器内部错误: " + e.getMessage()));
        }
    }
    
    /**
     * 从Redis获取对话历史
     */
    private ResponseEntity<?> getConversationsFromRedis(String conversationId, String username) {
        // 从Redis获取对话历史
        String key = "conversation:" + conversationId;
        String json = redisTemplate.opsForValue().get(key);
        
        List<Map<String, Object>> formattedConversations = new ArrayList<>();
        if (json != null) {
            try {
                // 将原始Redis数据转换为前端可用的格式
                List<Map<String, String>> history = objectMapper.readValue(json, 
                        new TypeReference<List<Map<String, String>>>() {});
                
                // 将对话转换为前端需要的格式
                for (int i = 0; i < history.size(); i += 2) {
                    if (i + 1 < history.size()) {
                        Map<String, String> userMsg = history.get(i);
                        Map<String, String> assistantMsg = history.get(i + 1);
                        
                        if ("user".equals(userMsg.get("role")) && "assistant".equals(assistantMsg.get("role"))) {
                            Map<String, Object> conversation = new HashMap<>();
                            conversation.put("question", userMsg.get("content"));
                            conversation.put("answer", assistantMsg.get("content"));
                            formattedConversations.add(conversation);
                        }
                    }
                }
                
                logger.info("从Redis中获取到 {} 条对话记录，会话ID: {}", formattedConversations.size(), conversationId);
            } catch (JsonProcessingException e) {
                logger.error("解析对话历史出错: {}", e.getMessage(), e);
                throw new CustomException("解析对话历史失败", HttpStatus.INTERNAL_SERVER_ERROR);
            }
        } else {
            logger.warn("会话ID {} 在Redis中找不到对应的历史记录", conversationId);
        }
        
        return ResponseEntity.ok().body(Map.of("data", formattedConversations));
    }
    
    /**
     * 解析日期时间字符串，支持多种格式
     */
    private LocalDateTime parseDateTime(String dateTimeStr) {
        if (dateTimeStr == null || dateTimeStr.trim().isEmpty()) {
            return null;
        }
        
        try {
            // 尝试标准格式解析 (2023-01-01T12:00:00)
            return LocalDateTime.parse(dateTimeStr);
        } catch (DateTimeParseException e1) {
            try {
                // 尝试解析不带秒的格式 (2023-01-01T12:00)
                if (dateTimeStr.length() == 16) {
                    return LocalDateTime.parse(dateTimeStr + ":00");
                }
                
                // 尝试解析不带分钟和秒的格式 (2023-01-01T12)
                if (dateTimeStr.length() == 13) {
                    return LocalDateTime.parse(dateTimeStr + ":00:00");
                }
                
                // 尝试解析日期格式 (2023-01-01)
                if (dateTimeStr.length() == 10) {
                    return LocalDateTime.parse(dateTimeStr + "T00:00:00");
                }
                
                // 如果以上都失败，尝试使用自定义格式解析
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
                return LocalDateTime.parse(dateTimeStr, formatter);
            } catch (Exception e2) {
                logger.error("无法解析日期时间: {}", dateTimeStr, e2);
                throw new CustomException("无效的日期格式: " + dateTimeStr, HttpStatus.BAD_REQUEST);
            }
        }
    }

    /**
     * 管理员查询所有对话历史
     */
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllConversations(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String target_user) {
        try {
            // 验证管理员权限
            String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
            User admin = userRepository.findByUsername(adminUsername)
                    .orElseThrow(() -> new CustomException("用户不存在", HttpStatus.NOT_FOUND));
            
            if (admin.getRole() != User.Role.ADMIN) {
                throw new CustomException("需要管理员权限", HttpStatus.FORBIDDEN);
            }
            
            List<Map<String, Object>> allConversations = new ArrayList<>();
            
            // 如果指定了目标用户，则只查询该用户的对话
            if (target_user != null && !target_user.isEmpty()) {
                User targetUser = userRepository.findByUsername(target_user)
                        .orElseThrow(() -> new CustomException("目标用户不存在", HttpStatus.NOT_FOUND));
                
                // 尝试不同格式的用户ID
                List<String> possibleUserIds = new ArrayList<>();
                possibleUserIds.add(targetUser.getId().toString());
                possibleUserIds.add(target_user);
                possibleUserIds.add(String.valueOf(targetUser.getId()));
                
                for (String userId : possibleUserIds) {
                    String conversationId = redisTemplate.opsForValue().get("user:" + userId + ":current_conversation");
                    if (conversationId != null) {
                        String key = "conversation:" + conversationId;
                        String json = redisTemplate.opsForValue().get(key);
                        
                        if (json != null) {
                            processRedisConversation(json, allConversations, target_user);
                            break;
                        }
                    }
                }
            } else {
                // 查询所有用户的对话
                List<User> allUsers = userRepository.findAll();
                
                for (User user : allUsers) {
                    // 尝试不同格式的用户ID
                    List<String> possibleUserIds = new ArrayList<>();
                    possibleUserIds.add(user.getId().toString());
                    possibleUserIds.add(user.getUsername());
                    possibleUserIds.add(String.valueOf(user.getId()));
                    
                    for (String userId : possibleUserIds) {
                        String conversationId = redisTemplate.opsForValue().get("user:" + userId + ":current_conversation");
                        
                        if (conversationId != null) {
                            String key = "conversation:" + conversationId;
                            String json = redisTemplate.opsForValue().get(key);
                            
                            if (json != null) {
                                processRedisConversation(json, allConversations, user.getUsername());
                                break;
                            }
                        }
                    }
                }
            }
            
            logger.info("管理员查询到 {} 条对话记录", allConversations.size());
            return ResponseEntity.ok().body(Map.of("data", allConversations));
        } catch (CustomException e) {
            logger.error("获取所有对话历史失败: {}", e.getMessage(), e);
            return ResponseEntity.status(e.getStatus()).body(Map.of("code", e.getStatus().value(), "message", e.getMessage()));
        } catch (Exception e) {
            logger.error("获取所有对话历史失败: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("code", 500, "message", "服务器内部错误: " + e.getMessage()));
        }
    }
    
    /**
     * 处理从Redis获取的对话数据
     */
    private void processRedisConversation(String json, List<Map<String, Object>> targetList, String username) throws JsonProcessingException {
        List<Map<String, String>> history = objectMapper.readValue(json, 
                new TypeReference<List<Map<String, String>>>() {});
        
        for (int i = 0; i < history.size(); i += 2) {
            if (i + 1 < history.size()) {
                Map<String, String> userMsg = history.get(i);
                Map<String, String> assistantMsg = history.get(i + 1);
                
                if ("user".equals(userMsg.get("role")) && "assistant".equals(assistantMsg.get("role"))) {
                    Map<String, Object> conversation = new HashMap<>();
                    conversation.put("username", username);
                    conversation.put("question", userMsg.get("content"));
                    conversation.put("answer", assistantMsg.get("content"));
                    targetList.add(conversation);
                }
            }
        }
    }
}
package com.yizhaoqi.smartpai.controller;

import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.Conversation;
import com.yizhaoqi.smartpai.service.ConversationService;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users/conversations")
public class ConversationController {
    private static final Logger logger = LoggerFactory.getLogger(ConversationController.class);


    @Autowired
    private ConversationService conversationService;

    @Autowired
    private JwtUtils jwtUtils;

    /**
     * 记录对话历史。
     */
    @PostMapping
    public ResponseEntity<?> recordConversation(
            @RequestHeader("Authorization") String token,
            @RequestBody ConversationRequest request) {
        String username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        if (username == null || username.isEmpty()) {
            throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
        }
        try {
            conversationService.recordConversation(username, request.getQuestion(), request.getAnswer());
        } catch (CustomException e) {
            // 记录完整的异常信息
            logger.error("Record conversation failed: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of(e.getStatus().value(), e.getMessage()));
        }catch (Exception e) {
            logger.error("Record conversation failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(500, "Internal server error"));
        }
        return ResponseEntity.ok().body(Map.of("message", "Conversation recorded successfully"));
    }

    /**
     * 查询对话历史。
     */
    @GetMapping
    public ResponseEntity<?> getConversations(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date) {
        String username = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        if (username == null || username.isEmpty()) {
            throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
        }
        List<Conversation> conversations = null;
        try {
            LocalDateTime startDate = start_date != null ? LocalDateTime.parse(start_date) : null;
            LocalDateTime endDate = end_date != null ? LocalDateTime.parse(end_date) : null;
            conversations = conversationService.getConversations(username, startDate, endDate);
        } catch (CustomException e) {
            // 记录完整的异常信息
            logger.error("Get conversations failed: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of(e.getStatus().value(), e.getMessage()));
        } catch (Exception e) {
            logger.error("Get conversations failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(500, "Internal server error"));
        }
        return ResponseEntity.ok().body(Map.of("data", conversations));
    }

    /**
     * 管理员查询所有用户的对话历史。
     */
    @GetMapping("/admin/all")
    public ResponseEntity<?> getAllConversations(
            @RequestHeader("Authorization") String token,
            @RequestParam(required = false) String target_user,
            @RequestParam(required = false) String start_date,
            @RequestParam(required = false) String end_date) {
        String adminUsername = jwtUtils.extractUsernameFromToken(token.replace("Bearer ", ""));
        if (adminUsername == null || adminUsername.isEmpty()) {
            throw new CustomException("Invalid token", HttpStatus.UNAUTHORIZED);
        }
        
        List<Conversation> conversations = null;
        try {
            LocalDateTime startDate = start_date != null ? LocalDateTime.parse(start_date) : null;
            LocalDateTime endDate = end_date != null ? LocalDateTime.parse(end_date) : null;
            conversations = conversationService.getAllConversations(adminUsername, target_user, startDate, endDate);
        } catch (CustomException e) {
            logger.error("Get all conversations failed: ", e);
            return ResponseEntity.status(e.getStatus()).body(Map.of(e.getStatus().value(), e.getMessage()));
        } catch (Exception e) {
            logger.error("Get all conversations failed: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(500, "Internal server error"));
        }
        
        return ResponseEntity.ok().body(Map.of("data", conversations));
    }
}

class ConversationRequest {
    private String question;
    private String answer;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }
}
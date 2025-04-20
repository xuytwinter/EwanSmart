package com.yizhaoqi.smartpai.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yizhaoqi.smartpai.service.ChatHandler;
import com.yizhaoqi.smartpai.utils.JwtUtils;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final ChatHandler chatHandler;
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JwtUtils jwtUtils;

    public ChatWebSocketHandler(ChatHandler chatHandler, JwtUtils jwtUtils) {
        this.chatHandler = chatHandler;
        this.jwtUtils = jwtUtils;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = extractUserId(session);
        sessions.put(userId, session);
        logger.info("WebSocket连接已建立，用户ID: {}，会话ID: {}，URI路径: {}", 
                    userId, session.getId(), session.getUri().getPath());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = extractUserId(session);
        try {
            // 直接使用消息内容，不进行 JSON 解析
            String userMessage = message.getPayload();
            logger.info("接收到消息，用户ID: {}，会话ID: {}，消息长度: {}", 
                       userId, session.getId(), userMessage.length());
            
            chatHandler.processMessage(userId, userMessage, session);
        } catch (Exception e) {
            logger.error("处理消息出错，用户ID: {}，会话ID: {}，错误: {}", 
                        userId, session.getId(), e.getMessage(), e);
            sendErrorMessage(session, "消息处理失败：" + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = extractUserId(session);
        sessions.remove(userId);
        logger.info("WebSocket连接已关闭，用户ID: {}，会话ID: {}，状态: {}", 
                    userId, session.getId(), status);
    }

    private String extractUserId(WebSocketSession session) {
        String path = session.getUri().getPath();
        String[] segments = path.split("/");
        String token = segments[segments.length - 1];
        
        // 从JWT令牌中提取用户名
        String username = jwtUtils.extractUsernameFromToken(token);
        if (username == null) {
            logger.warn("无法从令牌中提取用户名，使用令牌作为用户ID: {}", token);
            return token;
        }
        
        logger.debug("从JWT令牌中提取的用户名: {}", username);
        return username;
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            Map<String, String> error = Map.of("error", errorMessage);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
            logger.info("已发送错误消息到会话: {}, 错误: {}", session.getId(), errorMessage);
        } catch (Exception e) {
            logger.error("发送错误消息失败: {}", e.getMessage(), e);
        }
    }
} 
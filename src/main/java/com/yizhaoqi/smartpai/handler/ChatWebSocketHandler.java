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
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {
    
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    private final ChatHandler chatHandler;
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatWebSocketHandler(ChatHandler chatHandler) {
        this.chatHandler = chatHandler;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String userId = extractUserId(session);
        sessions.put(userId, session);
        logger.info("WebSocket connection established for user: {}", userId);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String userId = extractUserId(session);
        try {
            // 直接使用消息内容，不进行 JSON 解析
            String userMessage = message.getPayload();
            chatHandler.processMessage(userId, userMessage, session);
        } catch (Exception e) {
            logger.error("Error processing message for user {}: {}", userId, e.getMessage());
            sendErrorMessage(session, "消息处理失败：" + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String userId = extractUserId(session);
        sessions.remove(userId);
        logger.info("WebSocket connection closed for user: {}", userId);
    }

    private String extractUserId(WebSocketSession session) {
        String path = session.getUri().getPath();
        String[] segments = path.split("/");
        return segments[segments.length - 1];
    }

    private void sendErrorMessage(WebSocketSession session, String errorMessage) {
        try {
            Map<String, String> error = Map.of("error", errorMessage);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(error)));
        } catch (Exception e) {
            logger.error("Error sending error message: {}", e.getMessage());
        }
    }
} 
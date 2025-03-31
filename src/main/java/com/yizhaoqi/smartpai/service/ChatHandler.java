package com.yizhaoqi.smartpai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.yizhaoqi.smartpai.client.DeepSeekClient;
import com.yizhaoqi.smartpai.entity.Message;
import com.yizhaoqi.smartpai.entity.SearchResult;
import com.yizhaoqi.smartpai.repository.RedisRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatHandler.class);
    private final RedisTemplate<String, String> redisTemplate;
    private final HybridSearchService searchService;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;

    public ChatHandler(RedisTemplate<String, String> redisTemplate,
    HybridSearchService searchService,
                      DeepSeekClient deepSeekClient) {
        this.redisTemplate = redisTemplate;
        this.searchService = searchService;
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = new ObjectMapper();
    }

    public void processMessage(String userId, String userMessage, WebSocketSession session) {
        try {
            // 1. 获取或创建会话 ID
            String conversationId = getOrCreateConversationId(userId);
            
            // 2. 获取对话历史
            List<Map<String, String>> history = getConversationHistory(conversationId);
            
            // 3. 执行混合搜索
            List<SearchResult> searchResults = searchService.search(userMessage, 5);
            
            // 4. 构建上下文
            String context = buildContext(searchResults);
            
            // 5. 调用 DeepSeek API 并处理流式响应
            deepSeekClient.streamResponse(userMessage, context, history, 
                chunk -> sendResponseChunk(session, chunk),
                error -> handleError(session, error));
            
            // 6. 更新对话历史
            updateConversationHistory(conversationId, userMessage, "");
            
        } catch (Exception e) {
            logger.error("Error processing message: {}", e.getMessage());
            handleError(session, e);
        }
    }

    private String getOrCreateConversationId(String userId) {
        String key = "user:" + userId + ":current_conversation";
        String conversationId = redisTemplate.opsForValue().get(key);
        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(key, conversationId, Duration.ofDays(7));
        }
        return conversationId;
    }

    private List<Map<String, String>> getConversationHistory(String conversationId) {
        String key = "conversation:" + conversationId;
        String json = redisTemplate.opsForValue().get(key);
        try {
            if (json == null) {
                return new ArrayList<>();
            }
            return objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
        } catch (JsonProcessingException e) {
            logger.error("Error parsing conversation history: {}", e.getMessage());
            return new ArrayList<>();
        }
    }

    private void updateConversationHistory(String conversationId, String userMessage, String response) {
        String key = "conversation:" + conversationId;
        List<Map<String, String>> history = getConversationHistory(conversationId);
        history.add(Map.of("role", "user", "content", userMessage));
        history.add(Map.of("role", "assistant", "content", response));
        try {
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(history), Duration.ofDays(7));
        } catch (JsonProcessingException e) {
            logger.error("Error updating conversation history: {}", e.getMessage());
        }
    }

    private String buildContext(List<SearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return "";
        }
        
        return searchResults.stream()
                .map(result -> {
                    // 根据实际的 SearchResult 结构调整获取内容的方式
                    String content = result.getTextContent();
                    double score = result.getScore();
                    // 可以添加来源信息
                    return String.format("相关度: %.2f\n%s", score, content);
                })
                .collect(Collectors.joining("\n\n"));
    }

    private void sendResponseChunk(WebSocketSession session, String chunk) {
        try {
            Map<String, String> response = Map.of("chunk", chunk);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            logger.error("Error sending response chunk: {}", e.getMessage());
        }
    }

    private void handleError(WebSocketSession session, Throwable error) {
        try {
            Map<String, String> errorResponse = Map.of("error", error.getMessage());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
        } catch (Exception e) {
            logger.error("Error sending error message: {}", e.getMessage());
        }
    }
}

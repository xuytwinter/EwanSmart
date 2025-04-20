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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class ChatHandler {

    private static final Logger logger = LoggerFactory.getLogger(ChatHandler.class);
    private final RedisTemplate<String, String> redisTemplate;
    private final HybridSearchService searchService;
    private final DeepSeekClient deepSeekClient;
    private final ObjectMapper objectMapper;
    
    // 用于存储每个会话的完整响应
    private final Map<String, StringBuilder> responseBuilders = new ConcurrentHashMap<>();
    // 用于跟踪每个会话的响应完成状态
    private final Map<String, CompletableFuture<String>> responseFutures = new ConcurrentHashMap<>();

    public ChatHandler(RedisTemplate<String, String> redisTemplate,
    HybridSearchService searchService,
                      DeepSeekClient deepSeekClient) {
        this.redisTemplate = redisTemplate;
        this.searchService = searchService;
        this.deepSeekClient = deepSeekClient;
        this.objectMapper = new ObjectMapper();
    }

    public void processMessage(String userId, String userMessage, WebSocketSession session) {
        logger.info("开始处理消息，用户ID: {}, 会话ID: {}", userId, session.getId());
        try {
            // 1. 获取或创建会话 ID
            String conversationId = getOrCreateConversationId(userId);
            logger.info("会话ID: {}, 用户ID: {}", conversationId, userId);
            
            // 为当前会话创建响应构建器
            responseBuilders.put(session.getId(), new StringBuilder());
            // 创建一个CompletableFuture来跟踪响应完成状态
            CompletableFuture<String> responseFuture = new CompletableFuture<>();
            responseFutures.put(session.getId(), responseFuture);
            
            // 2. 获取对话历史
            List<Map<String, String>> history = getConversationHistory(conversationId);
            logger.debug("获取到 {} 条历史对话", history.size());
            
            // 3. 执行带权限过滤的混合搜索
            List<SearchResult> searchResults = searchService.searchWithPermission(userMessage, userId, 5);
            logger.debug("搜索结果数量: {}", searchResults.size());
            
            // 4. 构建上下文
            String context = buildContext(searchResults);
            
            // 5. 调用 DeepSeek API 并处理流式响应
            logger.info("调用DeepSeek API生成回复");
            deepSeekClient.streamResponse(userMessage, context, history, 
                chunk -> {
                    // 累积响应内容
                    StringBuilder responseBuilder = responseBuilders.get(session.getId());
                    if (responseBuilder != null) {
                        responseBuilder.append(chunk);
                    }
                    sendResponseChunk(session, chunk);
                },
                error -> {
                    // 处理错误并完成future
                    handleError(session, error);
                    responseFuture.completeExceptionally(error);
                    // 清理会话响应构建器
                    responseBuilders.remove(session.getId());
                    responseFutures.remove(session.getId());
                });
            
            // 6. 启动一个后台任务检查并标记响应完成
            new Thread(() -> {
                try {
                    // 等待最多30秒，给API足够的响应时间
                    Thread.sleep(3000); // 先等待3秒钟，让API有时间开始响应
                    
                    // 获取当前累积的响应内容
                    StringBuilder responseBuilder = responseBuilders.get(session.getId());
                    
                    // 如果响应构建器存在并且已有内容，认为响应已完成
                    if (responseBuilder != null) {
                        // 记录最后2秒的响应变化，检测是否停止增长
                        String lastResponse = responseBuilder.toString();
                        int lastLength = lastResponse.length();
                        
                        Thread.sleep(2000); // 再等待2秒
                        
                        // 再次检查是否有新内容
                        if (responseBuilder.length() == lastLength) {
                            // 没有新内容，可以认为响应已完成
                            responseFuture.complete(responseBuilder.toString());
                            logger.info("DeepSeek响应已完成，长度: {}", responseBuilder.length());
                            
                            // 更新对话历史
                            String completeResponse = responseBuilder.toString();
                            updateConversationHistory(conversationId, userMessage, completeResponse);
                            
                            // 输出对话存储信息以便调试
                            String redisKey = "user:" + userId + ":current_conversation";
                            logger.info("对话存储信息 - Redis键: {}, 值: {}", redisKey, conversationId);
                            
                            // 清理会话响应构建器
                            responseBuilders.remove(session.getId());
                            responseFutures.remove(session.getId());
                            logger.info("消息处理完成，用户ID: {}", userId);
                        } else {
                            // 仍有新内容，继续等待
                            logger.debug("响应仍在继续，等待完成...");
                            // 再等待最多25秒
                            for (int i = 0; i < 5; i++) {
                                Thread.sleep(5000);
                                if (responseBuilder != null) {
                                    lastLength = responseBuilder.length();
                                    // 再次检查2秒内是否有新内容
                                    Thread.sleep(2000);
                                    if (responseBuilder.length() == lastLength) {
                                        // 没有新内容，可以认为响应已完成
                                        responseFuture.complete(responseBuilder.toString());
                                        
                                        // 更新对话历史
                                        String completeResponse = responseBuilder.toString();
                                        updateConversationHistory(conversationId, userMessage, completeResponse);
                                        
                                        // 输出对话存储信息以便调试
                                        String redisKey = "user:" + userId + ":current_conversation";
                                        logger.info("对话存储信息 - Redis键: {}, 值: {}", redisKey, conversationId);
                                        
                                        // 清理会话响应构建器
                                        responseBuilders.remove(session.getId());
                                        responseFutures.remove(session.getId());
                                        logger.info("消息处理完成，用户ID: {}", userId);
                                        return;
                                    }
                                }
                            }
                            
                            // 如果经过多次检查仍未完成，强制完成
                            if (!responseFuture.isDone()) {
                                responseFuture.complete(responseBuilder.toString());
                                
                                // 更新对话历史
                                String completeResponse = responseBuilder.toString();
                                updateConversationHistory(conversationId, userMessage, completeResponse);
                                
                                // 输出对话存储信息以便调试
                                String redisKey = "user:" + userId + ":current_conversation";
                                logger.info("对话存储信息 - Redis键: {}, 值: {}", redisKey, conversationId);
                                
                                // 清理会话响应构建器
                                responseBuilders.remove(session.getId());
                                responseFutures.remove(session.getId());
                                logger.info("消息处理强制完成，用户ID: {}", userId);
                            }
                        }
                    } else {
                        logger.warn("响应构建器为空，可能出现了错误");
                        responseFuture.completeExceptionally(new RuntimeException("响应构建器为空"));
                    }
                } catch (Exception e) {
                    logger.error("检查响应完成时出错: {}", e.getMessage(), e);
                    responseFuture.completeExceptionally(e);
                    
                    // 清理会话响应构建器
                    responseBuilders.remove(session.getId());
                    responseFutures.remove(session.getId());
                }
            }).start();
            
        } catch (Exception e) {
            logger.error("处理消息错误: {}", e.getMessage(), e);
            handleError(session, e);
            // 清理会话响应构建器
            responseBuilders.remove(session.getId());
            // 清理响应future
            CompletableFuture<String> future = responseFutures.remove(session.getId());
            if (future != null && !future.isDone()) {
                future.completeExceptionally(e);
            }
        }
    }

    private String getOrCreateConversationId(String userId) {
        String key = "user:" + userId + ":current_conversation";
        String conversationId = redisTemplate.opsForValue().get(key);
        
        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
            redisTemplate.opsForValue().set(key, conversationId, Duration.ofDays(7));
            logger.info("为用户 {} 创建新的会话ID: {}", userId, conversationId);
        } else {
            logger.info("获取到用户 {} 的现有会话ID: {}", userId, conversationId);
        }
        
        return conversationId;
    }

    private List<Map<String, String>> getConversationHistory(String conversationId) {
        String key = "conversation:" + conversationId;
        String json = redisTemplate.opsForValue().get(key);
        try {
            if (json == null) {
                logger.debug("会话 {} 没有历史记录", conversationId);
                return new ArrayList<>();
            }
            
            List<Map<String, String>> history = objectMapper.readValue(json, new TypeReference<List<Map<String, String>>>() {});
            logger.debug("读取到会话 {} 的 {} 条历史记录", conversationId, history.size());
            return history;
        } catch (JsonProcessingException e) {
            logger.error("解析对话历史出错: {}, 会话ID: {}", e.getMessage(), conversationId, e);
            return new ArrayList<>();
        }
    }

    private void updateConversationHistory(String conversationId, String userMessage, String response) {
        String key = "conversation:" + conversationId;
        List<Map<String, String>> history = getConversationHistory(conversationId);
        
        // 添加用户消息和系统响应
        history.add(Map.of("role", "user", "content", userMessage));
        history.add(Map.of("role", "assistant", "content", response));
        
        try {
            String json = objectMapper.writeValueAsString(history);
            redisTemplate.opsForValue().set(key, json, Duration.ofDays(7));
            logger.info("更新对话历史成功，会话ID: {}, 历史记录数: {}, 响应长度: {}", 
                        conversationId, history.size(), response.length());
        } catch (JsonProcessingException e) {
            logger.error("更新对话历史失败: {}, 会话ID: {}", e.getMessage(), conversationId, e);
        }
    }

    private String buildContext(List<SearchResult> searchResults) {
        if (searchResults == null || searchResults.isEmpty()) {
            return "";
        }
        
        return searchResults.stream()
                .map(result -> {
                    // 包含内容、相关度和权限信息
                    String content = result.getTextContent();
                    double score = result.getScore();
                    String userId = result.getUserId() != null ? result.getUserId() : "系统";
                    String orgTag = result.getOrgTag() != null ? result.getOrgTag() : "公共";
                    String visibility = result.getIsPublic() != null && result.getIsPublic() ? "公开" : "私有";
                    
                    // 构建格式化的上下文信息
                    return String.format("相关度: %.2f | 所有者: %s | 组织: %s | 可见性: %s\n%s", 
                            score, userId, orgTag, visibility, content);
                })
                .collect(Collectors.joining("\n\n"));
    }

    private void sendResponseChunk(WebSocketSession session, String chunk) {
        try {
            Map<String, String> response = Map.of("chunk", chunk);
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        } catch (Exception e) {
            logger.error("发送响应片段失败: {}, 会话ID: {}", e.getMessage(), session.getId(), e);
        }
    }

    private void handleError(WebSocketSession session, Throwable error) {
        try {
            Map<String, String> errorResponse = Map.of("error", error.getMessage());
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(errorResponse)));
            logger.error("已发送错误消息到会话: {}, 错误: {}", session.getId(), error.getMessage());
        } catch (Exception e) {
            logger.error("发送错误消息失败: {}, 会话ID: {}", e.getMessage(), session.getId(), e);
        }
    }
}

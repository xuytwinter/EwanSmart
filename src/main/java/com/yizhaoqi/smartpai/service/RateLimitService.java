package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.RateLimitProperties;
import com.yizhaoqi.smartpai.exception.RateLimitExceededException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RateLimitService {

    private final StringRedisTemplate stringRedisTemplate;
    private final RateLimitProperties properties;

    public RateLimitService(StringRedisTemplate stringRedisTemplate, RateLimitProperties properties) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
    }

    public void checkRegisterByIp(String ip) {
        checkSingleWindow("register:ip:" + ip, properties.getRegister().getMax(), properties.getRegister().getWindowSeconds(), "注册请求过于频繁");
    }

    public void checkLoginByIp(String ip) {
        checkSingleWindow("login:ip:" + ip, properties.getLogin().getMax(), properties.getLogin().getWindowSeconds(), "登录请求过于频繁");
    }

    public void checkChatByUser(String userId) {
        checkSingleWindow("chat:user:" + userId, properties.getChatMessage().getMax(), properties.getChatMessage().getWindowSeconds(), "聊天请求过于频繁");
    }

    public void checkLlmByUser(String userId) {
        RateLimitProperties.DualWindowLimit limit = properties.getLlmRequest();
        checkSingleWindow("llm:min:user:" + userId, limit.getMinuteMax(), limit.getMinuteWindowSeconds(), "LLM调用过于频繁");
        checkSingleWindow("llm:day:user:" + userId, limit.getDayMax(), limit.getDayWindowSeconds(), "LLM当日调用次数已达上限");
    }

    public void checkEmbeddingByUser(String userId) {
        RateLimitProperties.DualWindowLimit limit = properties.getEmbeddingBatch();
        checkSingleWindow("emb:min:user:" + userId, limit.getMinuteMax(), limit.getMinuteWindowSeconds(), "向量化调用过于频繁");
        checkSingleWindow("emb:day:user:" + userId, limit.getDayMax(), limit.getDayWindowSeconds(), "向量化当日调用次数已达上限");
    }

    private void checkSingleWindow(String key, int max, long windowSeconds, String message) {
        Long current = stringRedisTemplate.opsForValue().increment(key);
        if (current == null) {
            return;
        }

        if (current == 1) {
            stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
        }

        if (current > max) {
            Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            long retryAfterSeconds = ttl == null || ttl < 0 ? windowSeconds : ttl;
            throw new RateLimitExceededException(message, retryAfterSeconds);
        }
    }
}

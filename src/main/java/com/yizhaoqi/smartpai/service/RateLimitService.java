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
    private final RateLimitConfigService rateLimitConfigService;
    private final UsageQuotaService usageQuotaService;

    public RateLimitService(
            StringRedisTemplate stringRedisTemplate,
            RateLimitProperties properties,
            RateLimitConfigService rateLimitConfigService,
            UsageQuotaService usageQuotaService
    ) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.properties = properties;
        this.rateLimitConfigService = rateLimitConfigService;
        this.usageQuotaService = usageQuotaService;
    }

    public void checkRegisterByIp(String ip) {
        checkSingleWindow("register:ip:" + ip, properties.getRegister().getMax(), properties.getRegister().getWindowSeconds(), "注册请求过于频繁");
    }

    public void checkLoginByIp(String ip) {
        checkSingleWindow("login:ip:" + ip, properties.getLogin().getMax(), properties.getLogin().getWindowSeconds(), "登录请求过于频繁");
    }

    public void checkChatByUser(String userId) {
        RateLimitConfigService.WindowLimitView limit = rateLimitConfigService.getCurrentSettings().chatMessage();
        checkSingleWindow("chat:user:" + userId, limit.max(), limit.windowSeconds(), "聊天请求过于频繁");
        usageQuotaService.recordChatRequest(userId);
    }

    public void checkLlmByUser(String userId) {
        RateLimitConfigService.DualWindowLimitView limit = rateLimitConfigService.getCurrentSettings().llmRequest();
        checkSingleWindow("llm:min:user:" + userId, limit.minuteMax(), limit.minuteWindowSeconds(), "LLM调用过于频繁");
        checkSingleWindow("llm:day:user:" + userId, limit.dayMax(), limit.dayWindowSeconds(), "LLM当日调用次数已达上限");
    }

    public void checkEmbeddingByUser(String userId) {
        RateLimitConfigService.DualWindowLimitView limit = rateLimitConfigService.getCurrentSettings().embeddingBatch();
        checkSingleWindow("emb:min:user:" + userId, limit.minuteMax(), limit.minuteWindowSeconds(), "向量化调用过于频繁");
        checkSingleWindow("emb:day:user:" + userId, limit.dayMax(), limit.dayWindowSeconds(), "向量化当日调用次数已达上限");
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

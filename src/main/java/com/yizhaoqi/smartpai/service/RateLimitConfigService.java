package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.RateLimitProperties;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.RateLimitConfig;
import com.yizhaoqi.smartpai.repository.RateLimitConfigRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RateLimitConfigService {

    private static final String CHAT_MESSAGE = "chat-message";
    private static final String LLM_REQUEST = "llm-request";
    private static final String EMBEDDING_BATCH = "embedding-batch";

    private final RateLimitProperties properties;
    private final RateLimitConfigRepository rateLimitConfigRepository;

    private volatile RateLimitSettingsView currentSettings;

    public RateLimitConfigService(RateLimitProperties properties, RateLimitConfigRepository rateLimitConfigRepository) {
        this.properties = properties;
        this.rateLimitConfigRepository = rateLimitConfigRepository;
        this.currentSettings = buildDefaultSettings();
    }

    @PostConstruct
    public void loadPersistedConfigs() {
        currentSettings = mergeOverrides(buildDefaultSettings(), rateLimitConfigRepository.findAll());
    }

    public RateLimitSettingsView getCurrentSettings() {
        return currentSettings;
    }

    public synchronized RateLimitSettingsView updateSettings(UpdateRateLimitRequest request, String updatedBy) {
        if (request == null) {
            throw new CustomException("限流配置不能为空", HttpStatus.BAD_REQUEST);
        }

        validateWindowLimit(request.chatMessage(), "聊天消息");
        validateDualWindowLimit(request.llmRequest(), "LLM 调用");
        validateDualWindowLimit(request.embeddingBatch(), "Embedding 调用");

        persistWindowLimit(CHAT_MESSAGE, request.chatMessage(), updatedBy);
        persistDualWindowLimit(LLM_REQUEST, request.llmRequest(), updatedBy);
        persistDualWindowLimit(EMBEDDING_BATCH, request.embeddingBatch(), updatedBy);

        currentSettings = new RateLimitSettingsView(
                request.chatMessage(),
                request.llmRequest(),
                request.embeddingBatch()
        );
        return currentSettings;
    }

    private RateLimitSettingsView buildDefaultSettings() {
        return new RateLimitSettingsView(
                new WindowLimitView(
                        properties.getChatMessage().getMax(),
                        properties.getChatMessage().getWindowSeconds()
                ),
                new DualWindowLimitView(
                        properties.getLlmRequest().getMinuteMax(),
                        properties.getLlmRequest().getMinuteWindowSeconds(),
                        properties.getLlmRequest().getDayMax(),
                        properties.getLlmRequest().getDayWindowSeconds()
                ),
                new DualWindowLimitView(
                        properties.getEmbeddingBatch().getMinuteMax(),
                        properties.getEmbeddingBatch().getMinuteWindowSeconds(),
                        properties.getEmbeddingBatch().getDayMax(),
                        properties.getEmbeddingBatch().getDayWindowSeconds()
                )
        );
    }

    private RateLimitSettingsView mergeOverrides(RateLimitSettingsView defaults, List<RateLimitConfig> configs) {
        WindowLimitView chatMessage = defaults.chatMessage();
        DualWindowLimitView llmRequest = defaults.llmRequest();
        DualWindowLimitView embeddingBatch = defaults.embeddingBatch();

        for (RateLimitConfig config : configs) {
            if (config == null || config.getConfigKey() == null) {
                continue;
            }

            switch (config.getConfigKey()) {
                case CHAT_MESSAGE -> {
                    if (config.getSingleMax() != null && config.getSingleWindowSeconds() != null) {
                        chatMessage = new WindowLimitView(config.getSingleMax(), config.getSingleWindowSeconds());
                    }
                }
                case LLM_REQUEST -> {
                    if (config.getMinuteMax() != null && config.getMinuteWindowSeconds() != null
                            && config.getDayMax() != null && config.getDayWindowSeconds() != null) {
                        llmRequest = new DualWindowLimitView(
                                config.getMinuteMax(),
                                config.getMinuteWindowSeconds(),
                                config.getDayMax(),
                                config.getDayWindowSeconds()
                        );
                    }
                }
                case EMBEDDING_BATCH -> {
                    if (config.getMinuteMax() != null && config.getMinuteWindowSeconds() != null
                            && config.getDayMax() != null && config.getDayWindowSeconds() != null) {
                        embeddingBatch = new DualWindowLimitView(
                                config.getMinuteMax(),
                                config.getMinuteWindowSeconds(),
                                config.getDayMax(),
                                config.getDayWindowSeconds()
                        );
                    }
                }
                default -> {
                    // Ignore unknown config rows so future expansions stay backward compatible.
                }
            }
        }

        return new RateLimitSettingsView(chatMessage, llmRequest, embeddingBatch);
    }

    private void persistWindowLimit(String key, WindowLimitView limit, String updatedBy) {
        RateLimitConfig config = rateLimitConfigRepository.findById(key).orElseGet(RateLimitConfig::new);
        config.setConfigKey(key);
        config.setSingleMax(limit.max());
        config.setSingleWindowSeconds(limit.windowSeconds());
        config.setMinuteMax(null);
        config.setMinuteWindowSeconds(null);
        config.setDayMax(null);
        config.setDayWindowSeconds(null);
        config.setUpdatedBy(updatedBy);
        rateLimitConfigRepository.save(config);
    }

    private void persistDualWindowLimit(String key, DualWindowLimitView limit, String updatedBy) {
        RateLimitConfig config = rateLimitConfigRepository.findById(key).orElseGet(RateLimitConfig::new);
        config.setConfigKey(key);
        config.setSingleMax(null);
        config.setSingleWindowSeconds(null);
        config.setMinuteMax(limit.minuteMax());
        config.setMinuteWindowSeconds(limit.minuteWindowSeconds());
        config.setDayMax(limit.dayMax());
        config.setDayWindowSeconds(limit.dayWindowSeconds());
        config.setUpdatedBy(updatedBy);
        rateLimitConfigRepository.save(config);
    }

    private void validateWindowLimit(WindowLimitView limit, String label) {
        if (limit == null) {
            throw new CustomException(label + "配置不能为空", HttpStatus.BAD_REQUEST);
        }
        if (limit.max() <= 0 || limit.windowSeconds() <= 0) {
            throw new CustomException(label + "配置必须为大于 0 的整数", HttpStatus.BAD_REQUEST);
        }
    }

    private void validateDualWindowLimit(DualWindowLimitView limit, String label) {
        if (limit == null) {
            throw new CustomException(label + "配置不能为空", HttpStatus.BAD_REQUEST);
        }
        if (limit.minuteMax() <= 0 || limit.minuteWindowSeconds() <= 0
                || limit.dayMax() <= 0 || limit.dayWindowSeconds() <= 0) {
            throw new CustomException(label + "配置必须为大于 0 的整数", HttpStatus.BAD_REQUEST);
        }
        if (limit.dayMax() < limit.minuteMax()) {
            throw new CustomException(label + "日限额不能小于分钟限额", HttpStatus.BAD_REQUEST);
        }
        if (limit.dayWindowSeconds() < limit.minuteWindowSeconds()) {
            throw new CustomException(label + "日窗口不能小于分钟窗口", HttpStatus.BAD_REQUEST);
        }
    }

    public record WindowLimitView(int max, long windowSeconds) {
    }

    public record DualWindowLimitView(int minuteMax, long minuteWindowSeconds, int dayMax, long dayWindowSeconds) {
    }

    public record RateLimitSettingsView(
            WindowLimitView chatMessage,
            DualWindowLimitView llmRequest,
            DualWindowLimitView embeddingBatch
    ) {
    }

    public record UpdateRateLimitRequest(
            WindowLimitView chatMessage,
            DualWindowLimitView llmRequest,
            DualWindowLimitView embeddingBatch
    ) {
    }
}

package com.yizhaoqi.smartpai.service;

import com.yizhaoqi.smartpai.config.RateLimitProperties;
import com.yizhaoqi.smartpai.exception.CustomException;
import com.yizhaoqi.smartpai.model.RateLimitConfig;
import com.yizhaoqi.smartpai.repository.RateLimitConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitConfigServiceTest {

    @Mock
    private RateLimitConfigRepository rateLimitConfigRepository;

    private RateLimitConfigService rateLimitConfigService;

    @BeforeEach
    void setUp() {
        RateLimitProperties properties = new RateLimitProperties();
        rateLimitConfigService = new RateLimitConfigService(properties, rateLimitConfigRepository);
    }

    @Test
    void shouldLoadPersistedOverrides() {
        when(rateLimitConfigRepository.findAll()).thenReturn(List.of(
                createWindowConfig("chat-message", 45, 90L),
                createDualConfig("llm-request", 25, 60L, 600, 86400L),
                createDualConfig("embedding-batch", 80, 60L, 2400, 86400L)
        ));

        rateLimitConfigService.loadPersistedConfigs();

        RateLimitConfigService.RateLimitSettingsView settings = rateLimitConfigService.getCurrentSettings();
        assertEquals(45, settings.chatMessage().max());
        assertEquals(90L, settings.chatMessage().windowSeconds());
        assertEquals(25, settings.llmRequest().minuteMax());
        assertEquals(600, settings.llmRequest().dayMax());
        assertEquals(80, settings.embeddingBatch().minuteMax());
        assertEquals(2400, settings.embeddingBatch().dayMax());
    }

    @Test
    void shouldPersistUpdatedSettings() {
        when(rateLimitConfigRepository.findById(anyString())).thenReturn(Optional.empty());
        when(rateLimitConfigRepository.save(any(RateLimitConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RateLimitConfigService.UpdateRateLimitRequest request = new RateLimitConfigService.UpdateRateLimitRequest(
                new RateLimitConfigService.WindowLimitView(40, 60L),
                new RateLimitConfigService.DualWindowLimitView(30, 60L, 700, 86400L),
                new RateLimitConfigService.DualWindowLimitView(70, 60L, 2200, 86400L)
        );

        RateLimitConfigService.RateLimitSettingsView updated = rateLimitConfigService.updateSettings(request, "admin");

        assertEquals(40, updated.chatMessage().max());
        assertEquals(30, updated.llmRequest().minuteMax());
        assertEquals(2200, updated.embeddingBatch().dayMax());
        verify(rateLimitConfigRepository, times(3)).save(any(RateLimitConfig.class));
    }

    @Test
    void shouldRejectInvalidDailyLimit() {
        RateLimitConfigService.UpdateRateLimitRequest request = new RateLimitConfigService.UpdateRateLimitRequest(
                new RateLimitConfigService.WindowLimitView(30, 60L),
                new RateLimitConfigService.DualWindowLimitView(50, 60L, 40, 86400L),
                new RateLimitConfigService.DualWindowLimitView(60, 60L, 2000, 86400L)
        );

        assertThrows(CustomException.class, () -> rateLimitConfigService.updateSettings(request, "admin"));
    }

    private RateLimitConfig createWindowConfig(String key, int max, long windowSeconds) {
        RateLimitConfig config = new RateLimitConfig();
        config.setConfigKey(key);
        config.setSingleMax(max);
        config.setSingleWindowSeconds(windowSeconds);
        config.setUpdatedBy("admin");
        return config;
    }

    private RateLimitConfig createDualConfig(String key, int minuteMax, long minuteWindowSeconds, int dayMax, long dayWindowSeconds) {
        RateLimitConfig config = new RateLimitConfig();
        config.setConfigKey(key);
        config.setMinuteMax(minuteMax);
        config.setMinuteWindowSeconds(minuteWindowSeconds);
        config.setDayMax(dayMax);
        config.setDayWindowSeconds(dayWindowSeconds);
        config.setUpdatedBy("admin");
        return config;
    }
}

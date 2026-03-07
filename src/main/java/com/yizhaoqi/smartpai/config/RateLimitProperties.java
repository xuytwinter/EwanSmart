package com.yizhaoqi.smartpai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "rate-limit")
public class RateLimitProperties {

    private final WindowLimit register = new WindowLimit(5, 3600);
    private final WindowLimit login = new WindowLimit(30, 60);
    private final WindowLimit chatMessage = new WindowLimit(30, 60);
    private final DualWindowLimit llmRequest = new DualWindowLimit(20, 60, 500, 86400);
    private final DualWindowLimit embeddingBatch = new DualWindowLimit(60, 60, 2000, 86400);

    public WindowLimit getRegister() {
        return register;
    }

    public WindowLimit getLogin() {
        return login;
    }

    public WindowLimit getChatMessage() {
        return chatMessage;
    }

    public DualWindowLimit getLlmRequest() {
        return llmRequest;
    }

    public DualWindowLimit getEmbeddingBatch() {
        return embeddingBatch;
    }

    public static class WindowLimit {
        private int max;
        private long windowSeconds;

        public WindowLimit() {
        }

        public WindowLimit(int max, long windowSeconds) {
            this.max = max;
            this.windowSeconds = windowSeconds;
        }

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }

        public long getWindowSeconds() {
            return windowSeconds;
        }

        public void setWindowSeconds(long windowSeconds) {
            this.windowSeconds = windowSeconds;
        }
    }

    public static class DualWindowLimit {
        private int minuteMax;
        private long minuteWindowSeconds;
        private int dayMax;
        private long dayWindowSeconds;

        public DualWindowLimit() {
        }

        public DualWindowLimit(int minuteMax, long minuteWindowSeconds, int dayMax, long dayWindowSeconds) {
            this.minuteMax = minuteMax;
            this.minuteWindowSeconds = minuteWindowSeconds;
            this.dayMax = dayMax;
            this.dayWindowSeconds = dayWindowSeconds;
        }

        public int getMinuteMax() {
            return minuteMax;
        }

        public void setMinuteMax(int minuteMax) {
            this.minuteMax = minuteMax;
        }

        public long getMinuteWindowSeconds() {
            return minuteWindowSeconds;
        }

        public void setMinuteWindowSeconds(long minuteWindowSeconds) {
            this.minuteWindowSeconds = minuteWindowSeconds;
        }

        public int getDayMax() {
            return dayMax;
        }

        public void setDayMax(int dayMax) {
            this.dayMax = dayMax;
        }

        public long getDayWindowSeconds() {
            return dayWindowSeconds;
        }

        public void setDayWindowSeconds(long dayWindowSeconds) {
            this.dayWindowSeconds = dayWindowSeconds;
        }
    }
}

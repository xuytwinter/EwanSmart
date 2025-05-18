package com.yizhaoqi.smartpai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {
    
    @Value("${embedding.api.url}")
    private String apiUrl;
    
    @Value("${embedding.api.key}")
    private String apiKey;
    
    @Bean
    public WebClient embeddingWebClient() {
        return WebClient.builder()
            .baseUrl(apiUrl)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }
} 
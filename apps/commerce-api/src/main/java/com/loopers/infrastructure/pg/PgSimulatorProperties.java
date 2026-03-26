package com.loopers.infrastructure.pg;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pg-simulator")
public record PgSimulatorProperties(
    String baseUrl,
    String userId,
    String callbackUrl,
    int connectTimeoutMs,
    int readTimeoutMs
) {
    public PgSimulatorProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "http://localhost:8081";
        }
        if (userId == null || userId.isBlank()) {
            userId = "commerce-api";
        }
        if (callbackUrl == null || callbackUrl.isBlank()) {
            callbackUrl = "http://localhost:8080/api/v1/payments/callback";
        }
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 800;
        }
        if (readTimeoutMs <= 0) {
            readTimeoutMs = 1200;
        }
    }
}

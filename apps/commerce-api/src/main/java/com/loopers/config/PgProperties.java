package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pg")
public record PgProperties(
    String baseUrl,
    String callbackUrl,
    TimeoutProperties timeout
) {
    public record TimeoutProperties(int connect, int read) {}
}

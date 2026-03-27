package com.loopers.infrastructure.pg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pg")
public record PgClientProperties(
        String baseUrl,
        int connectTimeoutMillis,
        int readTimeoutMillis
) {
}

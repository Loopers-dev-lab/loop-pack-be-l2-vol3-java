package com.loopers.infrastructure.payment.toss;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.toss")
public record TossProperties(
        String baseUrl,
        String secretKey,
        int connectTimeout,
        int readTimeout
) {
}

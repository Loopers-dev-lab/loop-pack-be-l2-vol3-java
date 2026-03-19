package com.loopers.infrastructure.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.toss")
public record TossProperties(
        String baseUrl,
        String secretKey,
        int connectTimeout,
        int readTimeout
) {
}

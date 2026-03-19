package com.loopers.infrastructure.payment.nice;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "payment.nice")
public record NiceProperties(
        String baseUrl,
        String clientKey,
        String secretKey,
        int connectTimeout,
        int readTimeout
) {
}

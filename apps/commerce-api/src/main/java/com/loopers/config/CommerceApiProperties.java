package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("commerce-api")
public record CommerceApiProperties(String callbackBaseUrl) {
}

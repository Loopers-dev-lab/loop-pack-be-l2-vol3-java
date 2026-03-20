package com.loopers.application.product.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("cache.public-product-detail")
public record PublicProductDetailCacheProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("30s") Duration ttl,
        @DefaultValue("false") boolean jitterEnabled,
        @DefaultValue("0.0") double jitterRatio
) {
}

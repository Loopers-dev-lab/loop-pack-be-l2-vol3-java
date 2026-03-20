package com.loopers.application.product.cache;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

@Component
public class PublicProductDetailCacheTtlPolicy {

    private final PublicProductDetailCacheProperties publicProductDetailCacheProperties;

    public PublicProductDetailCacheTtlPolicy(PublicProductDetailCacheProperties publicProductDetailCacheProperties) {
        this.publicProductDetailCacheProperties = publicProductDetailCacheProperties;
    }

    public Duration resolve() {
        Duration baseTtl = publicProductDetailCacheProperties.ttl();
        if (!publicProductDetailCacheProperties.jitterEnabled() || publicProductDetailCacheProperties.jitterRatio() <= 0) {
            return baseTtl;
        }

        return jitter(baseTtl, publicProductDetailCacheProperties.jitterRatio(), ThreadLocalRandom.current().nextDouble());
    }

    Duration jitter(Duration baseTtl, double jitterRatio, double randomValue) {
        long baseMillis = baseTtl.toMillis();
        if (baseMillis <= 0) {
            return baseTtl;
        }

        double boundedRandom = Math.max(0.0d, Math.min(1.0d, randomValue));
        double offsetRatio = (boundedRandom * 2.0d) - 1.0d;
        long jitterMillis = Math.round(baseMillis * jitterRatio * offsetRatio);
        long jitteredMillis = Math.max(1L, baseMillis + jitterMillis);
        return Duration.ofMillis(jitteredMillis);
    }
}

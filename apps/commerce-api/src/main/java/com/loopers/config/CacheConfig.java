package com.loopers.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.loopers.application.product.ProductReadModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<Long, ProductReadModel> productDetailCache(
        @Value("${cache.product.maximum-size:10000}") long maximumSize,
        @Value("${cache.product.expire-after-write-minutes:5}") long expireAfterWriteMinutes
    ) {
        return Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(expireAfterWriteMinutes, TimeUnit.MINUTES)
            .build();
    }
}

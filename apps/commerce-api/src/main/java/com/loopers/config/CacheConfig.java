package com.loopers.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig {

    // 캐시 키 전략:
    // - product:detail:{productId} → 상품 상세, TTL 10분
    // - product:list:{sort}        → 상품 목록, TTL 5분
    //
    // 무효화 전략 (Cache-aside + Evict):
    // - 좋아요 등록/취소 시 해당 상품 detail 캐시 + list 전체 캐시 삭제
    // - TTL 만료 시 자동 삭제 후 다음 조회에서 재적재
    //
    // 정합성 허용 범위:
    // - 좋아요 수: Evict로 즉시 무효화
    // - 상품 정보: TTL 만료까지 일시적 불일치 허용 (SOT = DB)

    @Bean
    public CacheManager cacheManager(LettuceConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .activateDefaultTyping(
                new ObjectMapper().getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL
            );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
            .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
            .disableCachingNullValues(); // Negative Cache 방지

        Map<String, RedisCacheConfiguration> cacheConfigurations = Map.of(
            "product:detail", defaultConfig.entryTtl(Duration.ofMinutes(10)),
            "product:list", defaultConfig.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(connectionFactory)
            .cacheDefaults(defaultConfig)
            .withInitialCacheConfigurations(cacheConfigurations)
            .build();
    }
}

package com.loopers.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;
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
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class CacheConfig implements CachingConfigurer {

    private static final Set<String> TWO_LEVEL_CACHES = Set.of(
            "productDetail",
            "statsOverview",
            "statsTopLiked",
            "statsTopOrdered"
    );

    private final LettuceConnectionFactory connectionFactory;

    @Bean
    public CacheManager cacheManager() {
        RedisCacheManager redisCacheManager = buildRedisCacheManager();
        CaffeineCacheManager caffeineCacheManager = buildCaffeineCacheManager();
        return new TwoLevelCacheManager(caffeineCacheManager, redisCacheManager, TWO_LEVEL_CACHES);
    }

    private RedisCacheManager buildRedisCacheManager() {
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(redisSerializer()))
                .disableCachingNullValues()
                .entryTtl(Duration.ofMinutes(10));

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                "productDetail", defaultConfig.entryTtl(Duration.ofMinutes(10)),
                "brandDetail", defaultConfig.entryTtl(Duration.ofMinutes(30)),
                "stockAvailable", defaultConfig.entryTtl(Duration.ofSeconds(30)),
                "authUser", defaultConfig.entryTtl(Duration.ofMinutes(1)),
                "statsOverview", defaultConfig.entryTtl(Duration.ofMinutes(5)),
                "statsDaily", defaultConfig.entryTtl(Duration.ofMinutes(5)),
                "statsTopLiked", defaultConfig.entryTtl(Duration.ofMinutes(5)),
                "statsTopOrdered", defaultConfig.entryTtl(Duration.ofMinutes(5)),
                "statsLowStock", defaultConfig.entryTtl(Duration.ofMinutes(5))
        );

        // productList는 Map.of 10개 제한으로 별도 추가
        Map<String, RedisCacheConfiguration> allConfigs = new java.util.HashMap<>(cacheConfigs);
        allConfigs.put("productList", defaultConfig.entryTtl(Duration.ofMinutes(5)));

        RedisCacheManager manager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(allConfigs)
                .build();
        manager.afterPropertiesSet();
        return manager;
    }

    private CaffeineCacheManager buildCaffeineCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1000)
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .recordStats());
        return manager;
    }

    private GenericJackson2JsonRedisSerializer redisSerializer() {
        ObjectMapper om = new ObjectMapper();
        om.registerModule(new JavaTimeModule());
        om.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        om.disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        om.activateDefaultTyping(
                BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        return new GenericJackson2JsonRedisSerializer(om);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache GET failed [cache={}, key={}]: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed [cache={}, key={}]: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache EVICT failed [cache={}, key={}]: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache CLEAR failed [cache={}]: {}", cache.getName(), e.getMessage());
            }
        };
    }
}

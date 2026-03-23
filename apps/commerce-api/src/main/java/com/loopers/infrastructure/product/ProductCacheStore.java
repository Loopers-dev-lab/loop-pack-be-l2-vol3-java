package com.loopers.infrastructure.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductInfo;
import com.loopers.application.product.ProductSearchCommand;
import com.loopers.config.redis.RedisConfig;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ProductCacheStore {

    private static final String DETAIL_KEY_PREFIX = "product:detail:";
    private static final String LIST_KEY_PREFIX = "product:list:";

    private static final Duration DETAIL_TTL = Duration.ofSeconds(180);
    private static final Duration LIST_TTL = Duration.ofSeconds(30);

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisTemplate<String, String> masterRedisTemplate;
    private final ObjectMapper objectMapper;

    public ProductCacheStore(
        RedisTemplate<String, String> redisTemplate,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
        ObjectMapper objectMapper
    ) {
        this.redisTemplate = redisTemplate;
        this.masterRedisTemplate = masterRedisTemplate;
        this.objectMapper = objectMapper;
    }

    public Optional<ProductInfo> getProduct(Long productId) {
        String key = detailKey(productId);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, ProductInfo.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize product cache. key={}", key, e);
            return Optional.empty();
        }
    }

    public void putProduct(Long productId, ProductInfo productInfo) {
        String key = detailKey(productId);
        try {
            masterRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(productInfo), DETAIL_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize product cache. key={}", key, e);
        }
    }

    public Optional<List<ProductInfo>> getProducts(ProductSearchCommand command) {
        String key = listKey(command);
        try {
            String value = redisTemplate.opsForValue().get(key);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(value, new TypeReference<>() {}));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize product list cache. key={}", key, e);
            return Optional.empty();
        }
    }

    public void putProducts(ProductSearchCommand command, List<ProductInfo> productInfos) {
        String key = listKey(command);
        try {
            masterRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(productInfos), LIST_TTL);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize product list cache. key={}", key, e);
        }
    }

    private String detailKey(Long productId) {
        return DETAIL_KEY_PREFIX + productId;
    }

    private String listKey(ProductSearchCommand command) {
        return LIST_KEY_PREFIX
            + "brandId=" + command.brandId()
            + ":sort=" + command.sortType()
            + ":page=" + command.page()
            + ":size=" + command.size();
    }
}

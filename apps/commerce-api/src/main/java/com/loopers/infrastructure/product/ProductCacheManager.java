package com.loopers.infrastructure.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCacheManager {

    private static final String DETAIL_KEY_PREFIX = "product:detail:";
    private static final String LIST_KEY_PREFIX = "product:list:";
    private static final String LIST_KEYS_REGISTRY = "product:list-keys";
    private static final Duration DETAIL_TTL = Duration.ofMinutes(10);
    private static final Duration LIST_TTL = Duration.ofMinutes(5);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    // Command

    public void putDetail(Long productId, ProductInfo info) {
        try {
            String json = objectMapper.writeValueAsString(info);
            redisTemplate.opsForValue().set(detailKey(productId), json, DETAIL_TTL);
        } catch (JsonProcessingException e) {
            log.warn("상품 상세 캐시 저장 실패. productId={}", productId, e);
        }
    }

    public void putList(Long brandId, String sort, int page, int size, CachedPage cachedPage) {
        try {
            String key = listKey(brandId, sort, page, size);
            String json = objectMapper.writeValueAsString(cachedPage);
            redisTemplate.opsForValue().set(key, json, LIST_TTL);
            redisTemplate.opsForSet().add(LIST_KEYS_REGISTRY, key);
        } catch (JsonProcessingException e) {
            log.warn("상품 목록 캐시 저장 실패. brandId={}, sort={}", brandId, sort, e);
        }
    }

    public void evictDetail(Long productId) {
        redisTemplate.delete(detailKey(productId));
        publishInvalidation("detail:" + productId);
    }

    public void evictAllLists() {
        Set<String> keys = redisTemplate.opsForSet().members(LIST_KEYS_REGISTRY);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        redisTemplate.delete(LIST_KEYS_REGISTRY);
        publishInvalidation("list:all");
    }

    private void publishInvalidation(String message) {
        try {
            redisTemplate.convertAndSend(ProductCacheInvalidationConfig.CHANNEL, message);
        } catch (Exception e) {
            log.warn("캐시 무효화 메시지 발행 실패: {}", message, e);
        }
    }

    // Query

    public Optional<ProductInfo> getDetail(Long productId) {
        String json = redisTemplate.opsForValue().get(detailKey(productId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ProductInfo.class));
        } catch (JsonProcessingException e) {
            log.warn("상품 상세 캐시 역직렬화 실패. productId={}", productId, e);
            return Optional.empty();
        }
    }

    public Optional<CachedPage> getList(Long brandId, String sort, int page, int size) {
        String json = redisTemplate.opsForValue().get(listKey(brandId, sort, page, size));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, CachedPage.class));
        } catch (JsonProcessingException e) {
            log.warn("상품 목록 캐시 역직렬화 실패. brandId={}, sort={}", brandId, sort, e);
            return Optional.empty();
        }
    }

    private String detailKey(Long productId) {
        return DETAIL_KEY_PREFIX + productId;
    }

    private String listKey(Long brandId, String sort, int page, int size) {
        String brandPart = brandId != null ? brandId.toString() : "all";
        return LIST_KEY_PREFIX + brandPart + ":" + sort + ":" + page + ":" + size;
    }

    public record CachedPage(List<ProductInfo> content, int page, int size, long totalElements) {
    }
}

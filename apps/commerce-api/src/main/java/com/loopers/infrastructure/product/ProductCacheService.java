package com.loopers.infrastructure.product;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductDetailInfo;
import com.loopers.application.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RequiredArgsConstructor
@Component
public class ProductCacheService {

    private final RedisTemplate<String, String> redisTemplate;  // 조회용 (replica 선호)
    private final ObjectMapper objectMapper;

    private static final Duration DETAIL_TTL = Duration.ofMinutes(10);
    private static final Duration LIST_TTL = Duration.ofMinutes(5);

    public Optional<ProductDetailInfo> getProductDetail(Long productId) {
        try {
            String key = "product:detail:" + productId;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ProductDetailInfo.class));
        } catch (Exception e) {
            log.warn("Redis 캐시 조회 실패: productId={}", productId, e);
            return Optional.empty();
        }
    }

    public void setProductDetail(Long productId, ProductDetailInfo info) {
        try {
            String key = "product:detail:" + productId;
            String json = objectMapper.writeValueAsString(info);
            redisTemplate.opsForValue().set(key, json, DETAIL_TTL);
        } catch (Exception e) {
            log.warn("Redis 캐시 저장 실패: productId={}", productId, e);
        }
    }

    public void evictProductDetail(Long productId) {
        try {
            String key = "product:detail:" + productId;
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis 캐시 삭제 실패: productId={}", productId, e);
        }
    }

    // ===== 상품 목록 캐시 =====

    public Optional<List<ProductDetailInfo>> getProductList(String sortType, Long brandId) {
        try {
            String key = buildListKey(sortType, brandId);
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, new TypeReference<List<ProductDetailInfo>>() {}));
        } catch (Exception e) {
            log.warn("Redis 목록 캐시 조회 실패: sortType={}, brandId={}", sortType, brandId, e);
            return Optional.empty();
        }
    }

    public void setProductList(String sortType, Long brandId, List<ProductDetailInfo> list) {
        try {
            String key = buildListKey(sortType, brandId);
            String json = objectMapper.writeValueAsString(list);
            redisTemplate.opsForValue().set(key, json, LIST_TTL);
        } catch (Exception e) {
            log.warn("Redis 목록 캐시 저장 실패: sortType={}, brandId={}", sortType, brandId, e);
        }
    }

    public void evictProductList() {
        try {
            List<String> keys = new ArrayList<>();
            for (ProductSortType sortType : ProductSortType.values()) {
                keys.add(buildListKey(sortType.name(), null)); // 전체 목록 키
            }
            redisTemplate.delete(keys);
        } catch (Exception e) {
            log.warn("Redis 목록 캐시 삭제 실패", e);
        }
    }

    private String buildListKey(String sortType, Long brandId) {
        if (brandId != null) {
            return "product:list:" + sortType + ":brand:" + brandId;
        }
        return "product:list:" + sortType;
    }
}

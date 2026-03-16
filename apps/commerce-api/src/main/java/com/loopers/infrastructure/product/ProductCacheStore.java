package com.loopers.infrastructure.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductDto;
import com.loopers.application.product.ProductSortType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductCacheStore {

    private static final String PRODUCT_KEY_PREFIX = "product:";
    private static final String PRODUCT_LIST_KEY_PREFIX = "product:list:";
    private static final Duration PRODUCT_TTL = Duration.ofHours(1);
    private static final Duration PRODUCT_LIST_TTL = Duration.ofMinutes(1);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public Optional<ProductDto.ProductInfo> getProduct(Long productId) {
        try {
            String json = redisTemplate.opsForValue().get(productKey(productId));
            if (json == null) return Optional.empty();
            return Optional.of(objectMapper.readValue(json, ProductDto.ProductInfo.class));
        } catch (Exception e) {
            log.warn("캐시 조회 실패 - product:{}", productId, e);
            return Optional.empty();
        }
    }

    public void putProduct(Long productId, ProductDto.ProductInfo info) {
        try {
            String json = objectMapper.writeValueAsString(info);
            redisTemplate.opsForValue().set(productKey(productId), json, PRODUCT_TTL);
        } catch (Exception e) {
            log.warn("캐시 저장 실패 - product:{}", productId, e);
        }
    }

    public void evictProduct(Long productId) {
        redisTemplate.delete(productKey(productId));
    }

    public Optional<Page<ProductDto.ProductInfo>> getProductList(Long brandId, ProductSortType sortType, int page, int size) {
        try {
            String json = redisTemplate.opsForValue().get(listKey(brandId, sortType, page, size));
            if (json == null) return Optional.empty();
            ProductListCache cache = objectMapper.readValue(json, ProductListCache.class);
            return Optional.of(new PageImpl<>(cache.content(), PageRequest.of(page, size), cache.totalElements()));
        } catch (Exception e) {
            log.warn("캐시 조회 실패 - product list brand:{} sort:{} page:{}", brandId, sortType, page, e);
            return Optional.empty();
        }
    }

    public void putProductList(Long brandId, ProductSortType sortType, int page, int size, Page<ProductDto.ProductInfo> result) {
        try {
            ProductListCache cache = new ProductListCache(result.getContent(), result.getTotalElements());
            String json = objectMapper.writeValueAsString(cache);
            redisTemplate.opsForValue().set(listKey(brandId, sortType, page, size), json, PRODUCT_LIST_TTL);
        } catch (Exception e) {
            log.warn("캐시 저장 실패 - product list brand:{} sort:{} page:{}", brandId, sortType, page, e);
        }
    }

    private String productKey(Long id) {
        return PRODUCT_KEY_PREFIX + id;
    }

    private String listKey(Long brandId, ProductSortType sortType, int page, int size) {
        return PRODUCT_LIST_KEY_PREFIX + "brand=" + brandId + ":sort=" + sortType + ":page=" + page + ":size=" + size;
    }

    private record ProductListCache(List<ProductDto.ProductInfo> content, long totalElements) {}
}

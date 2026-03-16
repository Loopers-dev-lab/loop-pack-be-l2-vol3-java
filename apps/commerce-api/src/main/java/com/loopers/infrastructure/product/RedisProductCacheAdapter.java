package com.loopers.infrastructure.product;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductCachePort;
import com.loopers.interfaces.api.product.ProductDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisProductCacheAdapter implements ProductCachePort {

    private static final String PRODUCT_DETAIL_KEY_PREFIX = "product:detail:";
    private static final String PRODUCT_LIST_KEY_PREFIX = "product:list:";
    private static final String PRODUCT_LIST_VERSION_KEY = "product:list:version";
    private static final long DETAIL_TTL_MINUTES = 10;
    private static final long LIST_TTL_MINUTES = 5;

    private final RedisTemplate<String, String> readTemplate;
    private final RedisTemplate<String, String> writeTemplate;
    private final ObjectMapper objectMapper;

    public RedisProductCacheAdapter(
        RedisTemplate<String, String> readTemplate,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> writeTemplate,
        ObjectMapper objectMapper
    ) {
        this.readTemplate = readTemplate;
        this.writeTemplate = writeTemplate;
        this.objectMapper = objectMapper;
    }

    // ── 상품 상세 캐시 ──

    @Override
    public ProductDto.ProductResponse getProductDetail(Long productId) {
        try {
            String key = PRODUCT_DETAIL_KEY_PREFIX + productId;
            String cached = readTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }
            return objectMapper.readValue(cached, ProductDto.ProductResponse.class);
        } catch (Exception e) {
            log.warn("Redis 상품 상세 캐시 조회 실패 (productId={}): {}", productId, e.getMessage());
            return null;
        }
    }

    @Override
    public void putProductDetail(Long productId, ProductDto.ProductResponse response) {
        try {
            String key = PRODUCT_DETAIL_KEY_PREFIX + productId;
            String json = objectMapper.writeValueAsString(response);
            writeTemplate.opsForValue().set(key, json, DETAIL_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis 상품 상세 캐시 저장 실패 (productId={}): {}", productId, e.getMessage());
        }
    }

    @Override
    public void evictProductDetail(Long productId) {
        try {
            String key = PRODUCT_DETAIL_KEY_PREFIX + productId;
            writeTemplate.delete(key);
        } catch (Exception e) {
            log.warn("Redis 상품 상세 캐시 삭제 실패 (productId={}): {}", productId, e.getMessage());
        }
    }

    // ── 상품 목록 캐시 (버전 기반 무효화) ──

    @Override
    public ProductDto.PagedProductResponse getProductList(Long brandId, String sort, int page, int size) {
        try {
            String key = buildListKey(brandId, sort, page, size);
            if (key == null) return null;
            String cached = readTemplate.opsForValue().get(key);
            if (cached == null) {
                return null;
            }
            return objectMapper.readValue(cached, ProductDto.PagedProductResponse.class);
        } catch (Exception e) {
            log.warn("Redis 상품 목록 캐시 조회 실패: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void putProductList(Long brandId, String sort, int page, int size, ProductDto.PagedProductResponse response) {
        try {
            String key = buildListKey(brandId, sort, page, size);
            if (key == null) return;
            String json = objectMapper.writeValueAsString(response);
            writeTemplate.opsForValue().set(key, json, LIST_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Redis 상품 목록 캐시 저장 실패: {}", e.getMessage());
        }
    }

    @Override
    public void evictProductList() {
        try {
            writeTemplate.opsForValue().increment(PRODUCT_LIST_VERSION_KEY);
        } catch (Exception e) {
            log.warn("Redis 상품 목록 캐시 버전 증가 실패: {}", e.getMessage());
        }
    }

    private String buildListKey(Long brandId, String sort, int page, int size) {
        try {
            String version = readTemplate.opsForValue().get(PRODUCT_LIST_VERSION_KEY);
            if (version == null) {
                version = "0";
                writeTemplate.opsForValue().setIfAbsent(PRODUCT_LIST_VERSION_KEY, "0");
            }
            String brandPart = brandId != null ? String.valueOf(brandId) : "all";
            return PRODUCT_LIST_KEY_PREFIX + "v" + version
                + ":brand:" + brandPart
                + ":sort:" + sort
                + ":page:" + page
                + ":size:" + size;
        } catch (Exception e) {
            log.warn("Redis 캐시 키 생성 실패: {}", e.getMessage());
            return null;
        }
    }
}

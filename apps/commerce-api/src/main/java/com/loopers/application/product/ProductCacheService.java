package com.loopers.application.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 상품 목록(PLP) 1페이지·상품 상세(PDP) Redis 캐시.
 * 로드맵: TTL 30~60초 + Jitter, 키 product:list:v1:..., product:detail:v1:{id}
 */
@Service
public class ProductCacheService {

    private static final String LIST_PREFIX = "product:list:v1:";
    private static final String DETAIL_PREFIX = "product:detail:v1:";
    private static final int TTL_SECONDS = 60;
    private static final int JITTER_SECONDS = 30;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ProductCacheService(
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    private static Duration ttlWithJitter() {
        return Duration.ofSeconds(TTL_SECONDS + ThreadLocalRandom.current().nextInt(0, JITTER_SECONDS + 1));
    }

    /** PLP 1페이지만 캐시 (page==0). brandId null이면 "all" */
    public Optional<Page<ProductListItemInfo>> getList(Long brandId, String sort, int size) {
        String key = listKey(brandId, sort, size);
        String json;
        try {
            json = redisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            // Redis 장애 시 캐시를 건너뛰고 DB 조회로 폴백
            return Optional.empty();
        }
        if (json == null) {
            return Optional.empty();
        }
        try {
            ProductListCacheDto dto = objectMapper.readValue(json, ProductListCacheDto.class);
            PageImpl<ProductListItemInfo> page = new PageImpl<>(
                    dto.content(),
                    PageRequest.of(dto.number(), dto.size()),
                    dto.totalElements());
            return Optional.of(page);
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public void putList(Long brandId, String sort, int size, Page<ProductListItemInfo> page) {
        String key = listKey(brandId, sort, size);
        ProductListCacheDto dto = new ProductListCacheDto(
                page.getTotalElements(),
                page.getNumber(),
                page.getSize(),
                page.getContent());
        try {
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(key, json, ttlWithJitter());
        } catch (JsonProcessingException | RuntimeException ignored) {
        }
    }

    public Optional<ProductDetailInfo> getDetail(Long productId) {
        String key = DETAIL_PREFIX + productId;
        String json;
        try {
            json = redisTemplate.opsForValue().get(key);
        } catch (RuntimeException e) {
            // Redis 장애 시 캐시를 건너뛰고 DB 조회로 폴백
            return Optional.empty();
        }
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ProductDetailInfo.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public void putDetail(Long productId, ProductDetailInfo info) {
        String key = DETAIL_PREFIX + productId;
        try {
            String json = objectMapper.writeValueAsString(info);
            redisTemplate.opsForValue().set(key, json, ttlWithJitter());
        } catch (JsonProcessingException | RuntimeException ignored) {
        }
    }

    /** 상품 수정/삭제 시 목록 캐시 무효화 (1페이지 전체) */
    public void evictList() {
        try {
            var keys = redisTemplate.keys(LIST_PREFIX + "*");
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (RuntimeException ignored) {
        }
    }

    /** 상품 수정/삭제·좋아요 변경 시 해당 상세 캐시 무효화 */
    public void evictDetail(Long productId) {
        try {
            redisTemplate.delete(DETAIL_PREFIX + productId);
        } catch (RuntimeException ignored) {
        }
    }

    private static String listKey(Long brandId, String sort, int size) {
        String b = brandId != null ? String.valueOf(brandId) : "all";
        return LIST_PREFIX + b + ":" + sort + ":0:" + size;
    }
}

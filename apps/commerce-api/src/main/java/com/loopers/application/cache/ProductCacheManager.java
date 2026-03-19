package com.loopers.application.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.product.ProductFacade;
import com.loopers.domain.product.ProductSortType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 상품 캐시 매니저 — Cache Decomposition 패턴
 *
 * 목록 캐시와 상세 캐시를 분리하여 무효화 범위를 최소화한다.
 *
 * 목록 캐시: products:list:{sort}:{brandId|all} → ID 리스트 + 메타데이터
 * 상세 캐시: products:detail:{productId} → ProductDetailResult (ProductInfo + BrandInfo)
 *
 * 조립 흐름 (첫 페이지):
 *   1. 목록 캐시에서 ID 리스트 조회
 *   2. MGET으로 상세 캐시 일괄 조회
 *   3. 미스 ID만 DB에서 조회 후 캐시 적재 (partial miss 처리)
 *   4. 원래 순서대로 조립하여 반환
 */
@Component
public class ProductCacheManager {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheManager.class);

    private static final String DETAIL_KEY_PREFIX = "products:detail:";
    private static final String LIST_KEY_PREFIX = "products:list:";
    private static final int DETAIL_TTL_BASE = 300;
    private static final int DETAIL_TTL_JITTER = 30;
    private static final int LIST_TTL_BASE = 60;
    private static final int LIST_TTL_JITTER = 10;

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public ProductCacheManager(RedisTemplate<String, String> redisTemplate,
                               ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // ── 상품 상세 캐시 (단건) ──

    public Optional<ProductFacade.ProductDetailResult> getProductDetail(Long productId) {
        try {
            String key = DETAIL_KEY_PREFIX + productId;
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ProductFacade.ProductDetailResult.class));
        } catch (Exception e) {
            log.warn("상품 상세 캐시 조회 실패 (productId={}), DB 폴백", productId, e);
            return Optional.empty();
        }
    }

    public void putProductDetail(Long productId, ProductFacade.ProductDetailResult result) {
        try {
            String key = DETAIL_KEY_PREFIX + productId;
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, json, ttlWithJitter(DETAIL_TTL_BASE, DETAIL_TTL_JITTER));
        } catch (Exception e) {
            log.warn("상품 상세 캐시 저장 실패 (productId={})", productId, e);
        }
    }

    // ── 상품 상세 캐시 (배치 — MGET) ──

    public Map<Long, ProductFacade.ProductDetailResult> getProductDetailBatch(List<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        try {
            List<String> keys = productIds.stream()
                    .map(id -> DETAIL_KEY_PREFIX + id)
                    .toList();

            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                return Map.of();
            }

            Map<Long, ProductFacade.ProductDetailResult> result = new HashMap<>();
            for (int i = 0; i < productIds.size(); i++) {
                String json = values.get(i);
                if (json != null) {
                    try {
                        result.put(productIds.get(i),
                                objectMapper.readValue(json, ProductFacade.ProductDetailResult.class));
                    } catch (JsonProcessingException e) {
                        log.warn("상품 상세 캐시 역직렬화 실패 (productId={})", productIds.get(i));
                    }
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("상품 상세 배치 캐시 조회 실패, DB 폴백", e);
            return Map.of();
        }
    }

    // ── 목록 캐시 (ID 리스트 + 메타데이터) ──

    public Optional<ProductListCache> getProductListIds(ProductSortType sort, Long brandId) {
        try {
            String key = listKey(sort, brandId);
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, ProductListCache.class));
        } catch (Exception e) {
            log.warn("상품 목록 캐시 조회 실패 (sort={}, brandId={}), DB 폴백", sort, brandId, e);
            return Optional.empty();
        }
    }

    public void putProductListIds(ProductSortType sort, Long brandId, ProductListCache cache) {
        try {
            String key = listKey(sort, brandId);
            String json = objectMapper.writeValueAsString(cache);
            redisTemplate.opsForValue().set(key, json, ttlWithJitter(LIST_TTL_BASE, LIST_TTL_JITTER));
        } catch (Exception e) {
            log.warn("상품 목록 캐시 저장 실패 (sort={}, brandId={})", sort, brandId, e);
        }
    }

    // ── afterCommit 캐시 무효화 ──

    /**
     * 상세 캐시만 삭제 — 좋아요 변경 시 사용.
     * 목록 캐시(ID 순서)는 TTL 만료에 의존한다.
     */
    public void registerDetailOnlyEvictAfterCommit(Long productId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        redisTemplate.delete(DETAIL_KEY_PREFIX + productId);
                    }
                }
        );
    }

    /**
     * 상세 + 목록 캐시 삭제 — 상품 정보 변경, 상태 변경, 삭제 시 사용.
     * 목록의 구성원이나 순서가 바뀌는 이벤트에서 호출한다.
     *
     * @param productId null이면 상세 삭제를 건너뛰고 목록만 삭제 (신규 등록 시)
     */
    public void registerEvictAfterCommit(Long productId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (productId != null) {
                            redisTemplate.delete(DETAIL_KEY_PREFIX + productId);
                        }
                        evictAllProductListCache();
                    }
                }
        );
    }

    /**
     * 브랜드 삭제 시 — 소속 상품 상세 캐시 일괄 삭제 + 목록 캐시 전체 삭제.
     */
    public void registerBrandDeleteEvictAfterCommit(List<Long> productIds) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        if (productIds != null && !productIds.isEmpty()) {
                            List<String> keys = productIds.stream()
                                    .map(id -> DETAIL_KEY_PREFIX + id)
                                    .toList();
                            redisTemplate.delete(keys);
                        }
                        evictAllProductListCache();
                    }
                }
        );
    }

    /**
     * 브랜드 상태 변경 시 — 목록 캐시만 삭제 (상품 상세에는 영향 없음).
     */
    public void registerListOnlyEvictAfterCommit() {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        evictAllProductListCache();
                    }
                }
        );
    }

    // ── 내부 메서드 ──

    /**
     * 목록 캐시 전체 삭제 — Redis SCAN 사용 (KEYS 대신).
     * 어드민 변경 시에만 호출되므로 빈도가 낮다.
     */
    private void evictAllProductListCache() {
        try {
            ScanOptions options = ScanOptions.scanOptions()
                    .match(LIST_KEY_PREFIX + "*")
                    .count(100)
                    .build();
            Set<String> keys = new HashSet<>();
            try (Cursor<String> cursor = redisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("상품 목록 캐시 삭제 실패 — TTL 안전망으로 자연 만료 대기", e);
        }
    }

    private String listKey(ProductSortType sort, Long brandId) {
        String sortPart = (sort != null) ? sort.name() : "LATEST";
        String brandPart = (brandId != null) ? String.valueOf(brandId) : "all";
        return LIST_KEY_PREFIX + sortPart + ":" + brandPart;
    }

    private Duration ttlWithJitter(int baseSeconds, int jitterRange) {
        int jitter = ThreadLocalRandom.current().nextInt(-jitterRange, jitterRange + 1);
        return Duration.ofSeconds(baseSeconds + jitter);
    }

    public record ProductListCache(
            List<Long> productIds,
            boolean hasNext,
            int size
    ) {}
}

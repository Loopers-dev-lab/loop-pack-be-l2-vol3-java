package com.loopers.application.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.brand.BrandInfo;
import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.ProductSortType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 상품 캐시 매니저
 *
 * Cache-Aside 패턴의 캐시 읽기/저장/무효화를 담당한다.
 * 무효화 전략: afterCommit DELETE + TTL 안전망
 *
 * 키 설계:
 *   상품 상세: products:detail:{productId}
 *   상품 목록: products:list:{sort}:{brandId|all}
 */
@Component
public class ProductCacheManager {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheManager.class);

    private static final String DETAIL_KEY_PREFIX = "products:detail:";
    private static final String LIST_KEY_PREFIX = "products:list:";
    private static final Duration DETAIL_TTL = Duration.ofSeconds(300);
    private static final Duration LIST_TTL = Duration.ofSeconds(60);

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final BrandService brandService;

    public ProductCacheManager(RedisTemplate<String, String> redisTemplate,
                               ObjectMapper objectMapper,
                               BrandService brandService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.brandService = brandService;
    }

    // ── 상품 상세 캐시 ──

    public Optional<ProductFacade.ProductDetailResult> getProductDetail(Long productId) {
        String key = DETAIL_KEY_PREFIX + productId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ProductFacade.ProductDetailResult.class));
        } catch (JsonProcessingException e) {
            log.warn("상품 상세 캐시 역직렬화 실패 (productId={}), 캐시 삭제 후 DB 조회", productId, e);
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    public void putProductDetail(Long productId, ProductFacade.ProductDetailResult result) {
        String key = DETAIL_KEY_PREFIX + productId;
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, json, DETAIL_TTL);
        } catch (JsonProcessingException e) {
            log.warn("상품 상세 캐시 직렬화 실패 (productId={})", productId, e);
        }
    }

    // ── 상품 목록 캐시 ──

    public Optional<ProductFacade.ProductCursorResult> getProductList(ProductSortType sort, Long brandId) {
        String key = listKey(sort, brandId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ProductFacade.ProductCursorResult.class));
        } catch (JsonProcessingException e) {
            log.warn("상품 목록 캐시 역직렬화 실패 (sort={}, brandId={}), 캐시 삭제 후 DB 조회", sort, brandId, e);
            redisTemplate.delete(key);
            return Optional.empty();
        }
    }

    public void putProductList(ProductSortType sort, Long brandId, ProductFacade.ProductCursorResult result) {
        String key = listKey(sort, brandId);
        try {
            String json = objectMapper.writeValueAsString(result);
            redisTemplate.opsForValue().set(key, json, LIST_TTL);
        } catch (JsonProcessingException e) {
            log.warn("상품 목록 캐시 직렬화 실패 (sort={}, brandId={})", sort, brandId, e);
        }
    }

    // ── afterCommit 캐시 무효화 ──

    /**
     * 상품 데이터 변경 시 호출.
     * afterCommit 콜백에서 캐시를 삭제한다. TTL이 최종 안전망.
     *
     * @param productId null이면 상세 캐시 삭제를 건너뛰고 목록만 삭제
     */
    public void registerEvictAfterCommit(Long productId) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        evictProductCaches(productId);
                    }
                }
        );
    }

    /**
     * 브랜드 삭제 시 호출 — 소속 상품 상세 캐시 + 목록 캐시 전체 삭제.
     * 브랜드 삭제 시 소속 상품이 전부 soft delete되므로 상세 캐시도 무효화 필수.
     */
    public void registerBrandDeleteEvictAfterCommit(List<Long> productIds) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        evictProductDetailBatch(productIds);
                        evictAllProductListCache();
                    }
                }
        );
    }

    /**
     * 브랜드 상태 변경 시 호출 — 상품 목록 캐시만 삭제 (상세는 브랜드 상태 무관).
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

    private void evictProductCaches(Long productId) {
        if (productId != null) {
            redisTemplate.delete(DETAIL_KEY_PREFIX + productId);
        }
        evictAllProductListCache();
    }

    private void evictProductDetailBatch(List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }
        List<String> keys = productIds.stream()
                .map(id -> DETAIL_KEY_PREFIX + id)
                .toList();
        redisTemplate.delete(keys);
    }

    /**
     * 상품 목록 캐시 전체 삭제.
     * 4개 정렬 × (all + 활성 브랜드) 키를 열거해서 Collection 일괄 삭제.
     */
    private void evictAllProductListCache() {
        try {
            List<Brand> activeBrands = brandService.getAllActiveBrands();
            List<String> keys = new ArrayList<>();

            for (ProductSortType sort : ProductSortType.values()) {
                keys.add(LIST_KEY_PREFIX + sort.name() + ":all");
                for (Brand brand : activeBrands) {
                    keys.add(LIST_KEY_PREFIX + sort.name() + ":" + brand.getId());
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
        String brandPart = (brandId != null) ? String.valueOf(brandId) : "all";
        return LIST_KEY_PREFIX + sort.name() + ":" + brandPart;
    }
}

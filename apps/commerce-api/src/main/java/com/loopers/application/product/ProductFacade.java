package com.loopers.application.product;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.config.redis.RedisConfig;
import com.loopers.domain.brand.BrandService;
import com.loopers.application.like.LikeApplicationService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductMetricsRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.SellingStatus;
import com.loopers.domain.event.UserActionEvent;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Component
public class ProductFacade {

    // 읽기 전용 조회 (Replica Preferred) - 캐시 히트 확인에 사용
    private final RedisTemplate<String, String> redisTemplate;
    // 쓰기 전용 (Master) - 캐시 저장 및 삭제에 사용
    private final RedisTemplate<String, String> masterRedisTemplate;

    private final ProductService productService;
    private final BrandService brandService;
    private final LikeApplicationService likeService;
    private final ProductMetricsRepository productMetricsRepository;
    private final RankingRedisRepository rankingRedisRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public ProductFacade(
        ProductService productService,
        BrandService brandService,
        LikeApplicationService likeService,
        ProductMetricsRepository productMetricsRepository,
        RankingRedisRepository rankingRedisRepository,
        RedisTemplate<String, String> redisTemplate,
        @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER) RedisTemplate<String, String> masterRedisTemplate,
        ObjectMapper objectMapper,
        ApplicationEventPublisher eventPublisher
    ) {
        this.productService = productService;
        this.brandService = brandService;
        this.likeService = likeService;
        this.productMetricsRepository = productMetricsRepository;
        this.rankingRedisRepository = rankingRedisRepository;
        this.redisTemplate = redisTemplate;
        this.masterRedisTemplate = masterRedisTemplate;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    public ProductInfo findById(Long productId) {
        return findById(productId, null);
    }

    /**
     * Cache-Aside 패턴으로 상품 상세를 조회한다.
     * - 캐시 히트: isLiked는 사용자별 데이터이므로 캐시에 저장하지 않고 별도 조회 후 오버레이
     * - 캐시 미스: DB 조회 후 isLiked=null 상태로 캐시 저장 (TTL 1분), 이후 isLiked 오버레이
     * - 캐시 키: product:detail:{productId}
     */
    public ProductInfo findById(Long productId, Long userId) {
        String cacheKey = "product:detail:" + productId;

        // 캐시 히트: isLiked, rank를 별도 조회해 오버레이 후 반환
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                ProductInfo info = objectMapper.readValue(cached, ProductInfo.class);
                Boolean isLiked = userId != null ? likeService.isLiked(userId, productId) : null;
                Integer rank = rankingRedisRepository.findRank(LocalDate.now(), productId);
                return info.withIsLiked(isLiked).withRank(rank);
            } catch (JsonProcessingException ignored) {
            }
        }

        // 캐시 미스: DB 조회 후 isLiked=null 상태로 캐시 저장
        // likeCount는 product_metrics에서 읽는다 (Eventual Consistency).
        Product product = productService.findById(productId);
        int likeCount = productMetricsRepository.findByProductId(productId)
            .map(m -> m.getLikeCount())
            .orElse(0);
        ProductInfo info = ProductInfo.from(product, likeCount, null);
        try {
            masterRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(info), Duration.ofMinutes(1));
        } catch (JsonProcessingException ignored) {
        }
        Boolean isLiked = userId != null ? likeService.isLiked(userId, productId) : null;
        Integer rank = rankingRedisRepository.findRank(LocalDate.now(), productId);
        if (userId != null) {
            eventPublisher.publishEvent(new UserActionEvent(
                UserActionEvent.EventType.PRODUCT_VIEWED, userId, productId, null
            ));
        }
        return info.withIsLiked(isLiked).withRank(rank);
    }

    /**
     * Cache-Aside 패턴으로 상품 목록을 조회한다.
     * - page == 0: 캐시 우선 조회 → 미스 시 DB 조회 후 캐시 저장 (TTL 5분)
     * - page > 0: 캐시 없이 DB 직접 조회 (페이지 수가 많아질수록 캐시 효용이 낮아 적용 제외)
     */
    public Page<ProductInfo> findAll(Long brandId, Pageable pageable) {
        // 첫 페이지가 아닌 경우 캐시 미적용
        if (pageable.getPageNumber() != 0) {
            return productService.findAll(brandId, pageable).map(ProductInfo::from);
        }

        String cacheKey = buildCacheKey(brandId, pageable);

        // 캐시 히트: 직렬화된 JSON을 PageImpl로 복원해 반환
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            try {
                ProductListCacheEntry entry = objectMapper.readValue(cached, ProductListCacheEntry.class);
                return new PageImpl<>(entry.content(), pageable, entry.totalElements());
            } catch (JsonProcessingException ignored) {
            }
        }

        // 캐시 미스: DB 조회 후 결과를 캐시에 저장
        Page<ProductInfo> result = productService.findAll(brandId, pageable).map(ProductInfo::from);
        try {
            ProductListCacheEntry entry = new ProductListCacheEntry(result.getContent(), result.getTotalElements());
            masterRedisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(entry), Duration.ofMinutes(5));
        } catch (JsonProcessingException ignored) {
        }
        return result;
    }

    public ProductInfo create(Long brandId, String name, String description, int price, int stock, SellingStatus sellingStatus) {
        brandService.findById(brandId);
        return ProductInfo.from(productService.create(brandId, name, description, price, stock, sellingStatus));
    }

    /**
     * 상품 수정 후 목록 캐시 및 상세 캐시를 무효화한다.
     * - 목록 캐시: brandId 기반 SCAN 패턴 삭제
     * - 상세 캐시: product:detail:{productId} exact key 삭제
     * update는 반환된 ProductInfo에서 brandId를 얻을 수 있으므로 별도 조회 불필요.
     */
    public ProductInfo update(Long productId, String name, String description, int price, SellingStatus sellingStatus) {
        ProductInfo info = ProductInfo.from(productService.update(productId, name, description, price, sellingStatus));
        evictProductListCache(info.brandId());
        masterRedisTemplate.delete("product:detail:" + productId);
        return info;
    }

    /**
     * 상품 삭제 후 목록 캐시 및 상세 캐시를 무효화한다.
     * - 목록 캐시: brandId 기반 SCAN 패턴 삭제
     * - 상세 캐시: product:detail:{productId} exact key 삭제
     * delete는 void 반환이므로 삭제 전에 별도로 조회해 brandId를 얻는다.
     */
    public void delete(Long productId) {
        Product product = productService.findById(productId);
        Long brandId = product.getBrandId();
        productService.delete(productId);
        evictProductListCache(brandId);
        masterRedisTemplate.delete("product:detail:" + productId);
    }

    /**
     * brandId와 관련된 캐시 키를 모두 삭제한다.
     * - "product:list:all:*"       : brandId 필터 없는 전체 목록 캐시
     * - "product:list:{brandId}:*" : 특정 브랜드 목록 캐시
     */
    private void evictProductListCache(Long brandId) {
        deleteByPattern("product:list:all:*");
        if (brandId != null) {
            deleteByPattern("product:list:" + brandId + ":*");
        }
    }

    /**
     * SCAN 기반으로 패턴에 일치하는 키를 순회해 삭제한다.
     * KEYS 명령은 싱글 스레드 Redis를 블로킹할 수 있어 운영 환경에서 사용 금지.
     */
    private void deleteByPattern(String pattern) {
        masterRedisTemplate.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
            List<byte[]> keys = new ArrayList<>();
            try (var cursor = connection.keyCommands().scan(options)) {
                cursor.forEachRemaining(keys::add);
            } catch (Exception ignored) {
            }
            if (!keys.isEmpty()) {
                connection.keyCommands().del(keys.toArray(new byte[0][]));
            }
            return null;
        });
    }

    /**
     * 캐시 키 형식: product:list:{brandId|all}:{sort}:0:{size}
     * 예) product:list:all:latest:0:20
     *     product:list:123:price_asc:0:20
     */
    private String buildCacheKey(Long brandId, Pageable pageable) {
        String brandPart = brandId != null ? String.valueOf(brandId) : "all";
        String sortPart = sortKey(pageable.getSort());
        return "product:list:" + brandPart + ":" + sortPart + ":0:" + pageable.getPageSize();
    }

    /**
     * Sort 객체를 캐시 키용 문자열로 변환한다.
     * ProductV1Controller.toSort()의 역매핑.
     */
    private String sortKey(Sort sort) {
        if (!sort.isSorted()) return "latest";
        Sort.Order order = sort.iterator().next();
        String prop = order.getProperty();
        if (prop.equals("createdAt")) return "latest";
        if (prop.equals("price.value")) return "price_asc";
        if (prop.equals("likeCount.value")) return "likes_desc";
        return prop + "_" + order.getDirection().name().toLowerCase();
    }
}

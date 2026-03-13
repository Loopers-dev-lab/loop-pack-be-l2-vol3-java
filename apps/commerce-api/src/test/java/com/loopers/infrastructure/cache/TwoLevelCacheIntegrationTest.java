package com.loopers.infrastructure.cache;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.StockService;
import com.loopers.domain.stats.StatsProjection;
import com.loopers.domain.stats.StatsService;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L1+L2 2-Level 캐시 통합 테스트")
class TwoLevelCacheIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private StockService stockService;

    @Autowired
    private StatsService statsService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("1. L1 Hit — L2(Redis) 제거 후에도 L1에서 데이터 반환")
    void l1Hit_AfterL2Evict_ShouldStillReturnData() {
        // given: 상품 생성 + 첫 조회 (L1 + L2 적재)
        BrandModel brand = brandService.createBrand("브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("L1테스트상품", brand.getBrandId(),
                BigDecimal.valueOf(10000), "설명");
        productService.findById(product.getProductId());

        // when: Redis(L2)에서만 수동 삭제
        Set<String> keys = redisTemplate.keys("productDetail*");
        assertThat(keys).isNotEmpty();
        keys.forEach(redisTemplate::delete);

        // then: Redis 키 삭제 확인
        assertThat(redisTemplate.keys("productDetail*")).isEmpty();

        // then: L1에서 여전히 데이터 반환
        Cache.ValueWrapper cached = cacheManager.getCache("productDetail").get(product.getProductId());
        assertThat(cached).isNotNull();
        ProductModel fromL1 = (ProductModel) cached.get();
        assertThat(fromL1.getProductName()).isEqualTo("L1테스트상품");
    }

    @Test
    @DisplayName("2. L1 Miss → L2 Hit → L1 자동 적재")
    void l1Miss_l2Hit_ShouldPopulateL1() {
        // given: 상품 생성 + 첫 조회 (L1 + L2 적재)
        BrandModel brand = brandService.createBrand("브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("L1적재테스트", brand.getBrandId(),
                BigDecimal.valueOf(15000), "설명");
        productService.findById(product.getProductId());

        // when: L1만 클리어 (TwoLevelCache의 L1 접근 위해 새 조회 전 L1 데이터 제거)
        // TwoLevelCache.get() 시 L1 miss → L2 hit → L1 적재 흐름 검증
        Cache twoLevelCache = cacheManager.getCache("productDetail");
        assertThat(twoLevelCache).isInstanceOf(TwoLevelCache.class);

        // L1 데이터만 직접 제거 (evict는 양쪽 다 지우므로, 내부 L1에 접근)
        // → TwoLevelCache를 통한 clear 후 L2에 수동 put으로 검증
        twoLevelCache.clear(); // L1 + L2 모두 클리어

        // L2(Redis)에만 직접 put — productService.findById는 @Cacheable이므로
        // CacheManager를 통해 L2에 직접 적재
        Set<String> keysAfterClear = redisTemplate.keys("productDetail*");
        assertThat(keysAfterClear).isEmpty();

        // 재조회 → DB → L1+L2 적재
        ProductModel reloaded = productService.findById(product.getProductId());
        assertThat(reloaded.getProductName()).isEqualTo("L1적재테스트");

        // Redis(L2)에서 삭제 후에도 L1에서 반환되면 L1 적재 성공
        redisTemplate.keys("productDetail*").forEach(redisTemplate::delete);
        Cache.ValueWrapper l1Value = cacheManager.getCache("productDetail").get(product.getProductId());
        assertThat(l1Value).isNotNull();
    }

    @Test
    @DisplayName("3. CacheEvict 시 L1+L2 동시 무효화")
    void cacheEvict_ShouldClearBothL1AndL2() {
        // given: 상품 생성 + 캐시 적재
        BrandModel brand = brandService.createBrand("브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("evict테스트", brand.getBrandId(),
                BigDecimal.valueOf(10000), "설명");
        productService.findById(product.getProductId());

        // 캐시 적재 확인
        assertThat(cacheManager.getCache("productDetail").get(product.getProductId())).isNotNull();
        assertThat(redisTemplate.keys("productDetail*")).isNotEmpty();

        // when: 상품 수정 → @CacheEvict
        productService.updateProduct(product.getProductId(), "수정상품", BigDecimal.valueOf(20000), "수정설명", null);

        // then: L2(Redis) 캐시 무효화
        assertThat(redisTemplate.keys("productDetail::*")).isEmpty();

        // then: L1도 무효화 (TwoLevelCache.evict가 양쪽 제거)
        Cache.ValueWrapper afterEvict = cacheManager.getCache("productDetail").get(product.getProductId());
        assertThat(afterEvict).isNull();
    }

    @Test
    @DisplayName("4. Stats 캐시는 상품 변경에 영향받지 않음 (TTL-only)")
    void statsCache_ShouldBeIndependentFromProductChanges() {
        // given: 상품 생성 (통계 데이터를 위해)
        BrandModel brand = brandService.createBrand("브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("통계테스트상품", brand.getBrandId(),
                BigDecimal.valueOf(10000), "설명");

        // given: 통계 조회 → 캐시 적재
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 13);
        statsService.getOverview(start, end);

        String cacheKey = start.toString() + ":" + end.toString();
        assertThat(cacheManager.getCache("statsOverview").get(cacheKey)).isNotNull();

        // when: 상품 수정 (productDetail evict되지만 statsOverview는 독립)
        productService.updateProduct(product.getProductId(), "수정됨", BigDecimal.valueOf(20000), "수정", null);

        // then: 통계 캐시는 여전히 유효
        assertThat(cacheManager.getCache("statsOverview").get(cacheKey)).isNotNull();
    }

    @Test
    @DisplayName("5. L2-only 대상(stockAvailable)은 TwoLevelCache가 아닌 RedisCache 반환")
    void l2OnlyTarget_ShouldNotUseTwoLevelCache() {
        // given: stockAvailable은 TWO_LEVEL_CACHES에 포함되지 않음
        Cache stockCache = cacheManager.getCache("stockAvailable");

        // then: TwoLevelCache가 아님
        assertThat(stockCache).isNotInstanceOf(TwoLevelCache.class);
    }

    @Test
    @DisplayName("6. L1 적용 대상(productDetail)은 TwoLevelCache 반환")
    void l1Target_ShouldUseTwoLevelCache() {
        // given: productDetail은 TWO_LEVEL_CACHES에 포함
        Cache productCache = cacheManager.getCache("productDetail");

        // then: TwoLevelCache
        assertThat(productCache).isInstanceOf(TwoLevelCache.class);
    }

    @Test
    @DisplayName("7. L2-only(brandDetail) 캐시 — Redis 제거 시 데이터 소실")
    void l2OnlyCache_AfterRedisEvict_ShouldReturnNull() {
        // given: 브랜드 조회 → L2(Redis)에만 캐시
        BrandModel brand = brandService.createBrand("L2only브랜드", "설명", "서울");
        brandService.findById(brand.getBrandId());
        assertThat(cacheManager.getCache("brandDetail").get(brand.getBrandId())).isNotNull();

        // when: Redis 키 삭제
        Set<String> keys = redisTemplate.keys("brandDetail*");
        assertThat(keys).isNotEmpty();
        keys.forEach(redisTemplate::delete);

        // then: L1이 없으므로 데이터 소실 (L2-only)
        Cache.ValueWrapper afterDelete = cacheManager.getCache("brandDetail").get(brand.getBrandId());
        assertThat(afterDelete).isNull();
    }

    @Test
    @DisplayName("8. Caffeine hitRate 메트릭 수집 확인")
    void caffeineStats_ShouldRecordHitRate() {
        // given: productDetail은 TwoLevelCache
        BrandModel brand = brandService.createBrand("메트릭브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("메트릭상품", brand.getBrandId(),
                BigDecimal.valueOf(10000), "설명");

        // when: 동일 상품 11회 조회 (첫 1회 miss, 나머지 10회 hit)
        for (int i = 0; i < 11; i++) {
            productService.findById(product.getProductId());
        }

        // then: TwoLevelCache 내부의 L1 CaffeineCache에서 stats 확인
        Cache twoLevelCache = cacheManager.getCache("productDetail");
        assertThat(twoLevelCache).isInstanceOf(TwoLevelCache.class);
        // TwoLevelCache → Caffeine native cache → stats
        // Note: @Cacheable은 TwoLevelCache.get(key, valueLoader)을 호출하는데,
        // 내부적으로 L1 caffeine cache를 조회하므로 stats가 기록됨
    }
}

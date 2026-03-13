package com.loopers.infrastructure.cache;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStockModel;
import com.loopers.domain.product.StockService;
import com.loopers.domain.stats.StatsProjection;
import com.loopers.domain.stats.StatsService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRegisterCommand;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Redis L2 캐시 통합 테스트")
class RedisCacheIntegrationTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private StockService stockService;

    @Autowired
    private UserService userService;

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
        // L1 (Caffeine) 캐시 클리어 — TRUNCATE로 ID 리셋 시 stale hit 방지
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
    @DisplayName("1. 상품 조회 시 최초 Cache Miss → Redis 저장, 두 번째 Cache Hit")
    void findById_SecondCall_ShouldHitCache() {
        // given
        BrandModel brand = brandService.createBrand("테스트브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("테스트상품", brand.getBrandId(),
                BigDecimal.valueOf(10000), "상품 설명");

        // when: 첫 번째 조회 (Cache Miss → DB → Redis 저장)
        ProductModel first = productService.findById(product.getProductId());

        // then: Redis 캐시에 저장됨
        Cache.ValueWrapper cached = cacheManager.getCache("productDetail").get(product.getProductId());
        assertThat(cached).isNotNull();

        // when: 두 번째 조회 (Cache Hit)
        ProductModel second = productService.findById(product.getProductId());

        // then: 동일 데이터 반환
        assertThat(first.getProductId()).isEqualTo(second.getProductId());
        assertThat(first.getProductName()).isEqualTo(second.getProductName());
        assertThat(first.getPrice()).isEqualByComparingTo(second.getPrice());
    }

    @Test
    @DisplayName("2. 상품 수정(CacheEvict) 후 재조회 시 캐시 무효화 → DB 재조회")
    void updateProduct_ShouldEvictCache() {
        // given
        BrandModel brand = brandService.createBrand("테스트브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("원래상품명", brand.getBrandId(),
                BigDecimal.valueOf(10000), "설명");

        // 캐시 적재
        productService.findById(product.getProductId());
        assertThat(cacheManager.getCache("productDetail").get(product.getProductId())).isNotNull();

        // when: 상품 수정 → CacheEvict
        productService.updateProduct(product.getProductId(), "수정된상품명",
                BigDecimal.valueOf(20000), "수정 설명", null);

        // then: 캐시 무효화됨
        assertThat(cacheManager.getCache("productDetail").get(product.getProductId())).isNull();

        // when: 재조회 → 수정된 데이터
        ProductModel updated = productService.findById(product.getProductId());
        assertThat(updated.getProductName()).isEqualTo("수정된상품명");
        assertThat(updated.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(20000));

        // then: 캐시에 수정된 데이터가 다시 적재됨
        assertThat(cacheManager.getCache("productDetail").get(product.getProductId())).isNotNull();
    }

    @Test
    @DisplayName("3. 상품 삭제(CacheEvict) 후 캐시 무효화 확인")
    void deleteProduct_ShouldEvictCache() {
        // given
        BrandModel brand = brandService.createBrand("테스트브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("삭제대상상품", brand.getBrandId(),
                BigDecimal.valueOf(10000), "설명");

        // 캐시 적재
        productService.findById(product.getProductId());
        assertThat(cacheManager.getCache("productDetail").get(product.getProductId())).isNotNull();

        // when: 삭제 → CacheEvict
        productService.deleteProduct(product.getProductId());

        // then: 캐시에서 제거됨
        assertThat(cacheManager.getCache("productDetail").get(product.getProductId())).isNull();
    }

    @Test
    @DisplayName("4. 직렬화 정합성 — 캐시 저장 후 역직렬화 시 필드값 일치")
    void serialization_ShouldPreserveAllFields() {
        // given
        BrandModel brand = brandService.createBrand("브랜드A", "설명", "서울");
        ProductModel product = productService.createProduct("직렬화테스트상품", brand.getBrandId(),
                BigDecimal.valueOf(15000), "직렬화 검증용 상품");

        // when: 캐시 적재
        productService.findById(product.getProductId());

        // then: Redis에서 키 존재 확인
        Set<String> keys = redisTemplate.keys("productDetail*");
        assertThat(keys).isNotEmpty();

        // then: 캐시에서 꺼낸 데이터와 원본 비교
        Object fromCache = cacheManager.getCache("productDetail")
                .get(product.getProductId()).get();
        assertThat(fromCache).isInstanceOf(ProductModel.class);
        ProductModel cachedProduct = (ProductModel) fromCache;
        assertThat(cachedProduct.getProductId()).isEqualTo(product.getProductId());
        assertThat(cachedProduct.getProductName()).isEqualTo("직렬화테스트상품");
        assertThat(cachedProduct.getBrandId()).isEqualTo(brand.getBrandId());
        assertThat(cachedProduct.getPrice()).isEqualByComparingTo(BigDecimal.valueOf(15000));
        assertThat(cachedProduct.getDescription()).isEqualTo("직렬화 검증용 상품");
    }

    @Test
    @DisplayName("5. Brand 캐시 Hit/Evict — 조회 → 수정 → 재조회")
    void brandCache_HitAndEvict() {
        // given
        BrandModel brand = brandService.createBrand("캐시브랜드", "설명", "서울");

        // when: 첫 조회 → 캐시 적재
        brandService.findById(brand.getBrandId());
        assertThat(cacheManager.getCache("brandDetail").get(brand.getBrandId())).isNotNull();

        // when: 수정 → CacheEvict
        brandService.updateBrand(brand.getBrandId(), "수정브랜드", "수정설명", "부산");

        // then: 캐시 무효화
        assertThat(cacheManager.getCache("brandDetail").get(brand.getBrandId())).isNull();

        // when: 재조회 → 수정된 데이터
        BrandModel updated = brandService.findById(brand.getBrandId());
        assertThat(updated.getBrandName()).isEqualTo("수정브랜드");

        // then: 캐시에 수정된 데이터 적재
        assertThat(cacheManager.getCache("brandDetail").get(brand.getBrandId())).isNotNull();
    }

    @Test
    @DisplayName("6. Stock 캐시 — hold 후 캐시 무효화 확인")
    void stockCache_HoldShouldEvict() {
        // given
        BrandModel brand = brandService.createBrand("브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("재고상품", brand.getBrandId(),
                BigDecimal.valueOf(10000), "설명");
        stockService.createStock(product.getProductId(), 10);

        // 캐시 적재
        ProductStockModel stock = stockService.findByProductId(product.getProductId());
        assertThat(stock.getOnHand()).isEqualTo(10);
        assertThat(cacheManager.getCache("stockAvailable").get(product.getProductId())).isNotNull();

        // when: hold → CacheEvict
        stockService.hold(product.getProductId(), 3);

        // then: 캐시 무효화 확인
        assertThat(cacheManager.getCache("stockAvailable").get(product.getProductId())).isNull();

        // when: 재조회 → 갱신된 재고
        ProductStockModel afterHold = stockService.findByProductId(product.getProductId());
        assertThat(afterHold.getAvailableQty()).isEqualTo(7);
    }

    @Test
    @DisplayName("7. Auth 캐시 보안 — 캐시된 유저로 틀린 비밀번호 인증 시 예외 발생")
    void authCache_WrongPassword_ShouldThrowEvenWhenCached() {
        // given
        String loginId = "cacheuser01";
        String password = "Test1234!@#";
        userService.register(new UserRegisterCommand(loginId, password, "테스트", "19900101", "test@test.com", "서울"));

        // 캐시 적재 (findByLoginId 호출)
        UserModel cached = userService.findByLoginId(loginId);
        assertThat(cached).isNotNull();
        assertThat(cacheManager.getCache("authUser").get(loginId)).isNotNull();

        // when & then: 틀린 비밀번호로 인증 → 예외 발생 (BCrypt 비교는 항상 수행)
        assertThatThrownBy(() -> userService.authenticate(loginId, "WrongPassword!@#"))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("8. Stats 캐시 TTL-only — 동일 조건 2회 조회 시 캐시 Hit")
    void statsCache_ShouldHitOnSecondCall() {
        // given
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 3, 13);

        // when: 첫 번째 조회 → DB → 캐시 적재
        StatsProjection.Overview first = statsService.getOverview(start, end);

        // then: 캐시에 저장됨
        String cacheKey = start.toString() + ":" + end.toString();
        assertThat(cacheManager.getCache("statsOverview").get(cacheKey)).isNotNull();

        // when: 두 번째 조회 → 캐시 Hit
        StatsProjection.Overview second = statsService.getOverview(start, end);

        // then: 동일 결과
        assertThat(first.getPendingCount()).isEqualTo(second.getPendingCount());
        assertThat(first.getCancelledCount()).isEqualTo(second.getCancelledCount());
        assertThat(first.getExpiredCount()).isEqualTo(second.getExpiredCount());
    }

    @Test
    @DisplayName("9. Brand 삭제 → 소속 상품 캐시 연쇄 evict")
    void brandDelete_ShouldCascadeEvictProductCache() {
        // given
        BrandModel brand = brandService.createBrand("삭제브랜드", "설명", "서울");
        ProductModel product1 = productService.createProduct("상품1", brand.getBrandId(),
                BigDecimal.valueOf(10000), "설명");
        ProductModel product2 = productService.createProduct("상품2", brand.getBrandId(),
                BigDecimal.valueOf(20000), "설명");

        // 상품 캐시 적재
        productService.findById(product1.getProductId());
        productService.findById(product2.getProductId());
        assertThat(cacheManager.getCache("productDetail").get(product1.getProductId())).isNotNull();
        assertThat(cacheManager.getCache("productDetail").get(product2.getProductId())).isNotNull();

        // 브랜드 캐시 적재
        brandService.findById(brand.getBrandId());
        assertThat(cacheManager.getCache("brandDetail").get(brand.getBrandId())).isNotNull();

        // when: 브랜드 삭제 (연쇄 삭제 + 캐시 evict)
        brandService.deleteBrand(brand.getBrandId());

        // then: 소속 상품 캐시 무효화
        assertThat(cacheManager.getCache("productDetail").get(product1.getProductId())).isNull();
        assertThat(cacheManager.getCache("productDetail").get(product2.getProductId())).isNull();
        // then: 브랜드 캐시도 무효화
        assertThat(cacheManager.getCache("brandDetail").get(brand.getBrandId())).isNull();
    }

    @Test
    @DisplayName("10. 동일 상품 100건 동시 조회 시 모두 성공 + 캐시 적재 확인")
    void concurrentRead_ShouldSucceedAndPopulateCache() throws Exception {
        // given
        BrandModel brand = brandService.createBrand("브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("동시성상품", brand.getBrandId(),
                BigDecimal.valueOf(10000), "설명");

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // when: 100개 스레드 동시 조회
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    productService.findById(product.getProductId());
                    successCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }
        startLatch.countDown();
        doneLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        // then: 모두 성공
        assertThat(successCount.get()).isEqualTo(threadCount);
        // then: 캐시에 데이터 적재됨
        assertThat(cacheManager.getCache("productDetail").get(product.getProductId())).isNotNull();
    }
}

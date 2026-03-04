package com.loopers.concurrency;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.inventory.InventoryService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.like.ProductLikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 제어 통합 테스트
 *
 * 실제 DB(Testcontainers)를 사용하여 동시 요청 시
 * Lock 전략이 정상 동작하는지 검증한다.
 */
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ConcurrencyIntegrationTest {

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private LikeService likeService;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductLikeRepository productLikeRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand createBrand() {
        return brandRepository.save(Brand.create("테스트브랜드", "설명"));
    }

    private Product createProduct(Long brandId) {
        return productRepository.save(Product.create(brandId, "테스트상품", "설명", 10000));
    }

    // ===== 1. 재고 동시 주문 (비관적 락) =====

    @Test
    @DisplayName("재고가 5개인 상품에 10명이 동시에 1개씩 예약하면, 정확히 5명만 성공한다")
    void 재고_동시_예약_비관적_락() throws InterruptedException {
        // arrange
        Brand brand = createBrand();
        Product product = createProduct(brand.getId());
        inventoryRepository.save(Inventory.create(product.getId(), 5));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    inventoryService.reserveAll(Map.of(product.getId(), 1));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // assert
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isZero();
    }

    // ===== 2. 쿠폰 동시 사용 (낙관적 락) =====

    @Test
    @DisplayName("동일 쿠폰을 10명이 동시에 사용하면, 정확히 1명만 성공한다")
    void 쿠폰_동시_사용_낙관적_락() throws InterruptedException {
        // arrange
        CouponTemplate template = couponTemplateRepository.save(
                CouponTemplate.create("테스트쿠폰", "설명", DiscountType.FIXED, 1000, null,
                        0, 100, 10,
                        ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30))
        );

        // 쿠폰 1장을 userId=1에게 발급
        IssuedCoupon issuedCoupon = issuedCouponRepository.save(IssuedCoupon.create(template.getId(), 1L));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act — 10개 스레드가 동일 쿠폰을 동시에 사용 시도
        for (int i = 0; i < threadCount; i++) {
            long orderId = 100L + i;
            executor.submit(() -> {
                try {
                    couponService.use(issuedCoupon.getId(), 1L, orderId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // assert — 낙관적 락으로 정확히 1명만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);
    }

    // ===== 3. 좋아요 동시 요청 (원자적 UPDATE) =====

    @Test
    @DisplayName("10명이 동시에 같은 상품에 좋아요하면, likeCount가 정확히 10이다")
    void 좋아요_동시_요청_원자적_UPDATE() throws InterruptedException {
        // arrange
        Brand brand = createBrand();
        Product product = createProduct(brand.getId());
        inventoryRepository.save(Inventory.create(product.getId(), 100));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        // act — 서로 다른 userId가 동시에 좋아요
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1L;
            executor.submit(() -> {
                try {
                    likeService.like(userId, product.getId());
                    productService.incrementLikeCount(product.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // assert — 원자적 UPDATE로 모두 성공, likeCount 정확히 10
        assertThat(errors).isEmpty();
        assertThat(successCount.get()).isEqualTo(threadCount);

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getLikeCount()).isEqualTo(threadCount);
    }
}

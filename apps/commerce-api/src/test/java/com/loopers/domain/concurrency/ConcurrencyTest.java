package com.loopers.domain.concurrency;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateService;
import com.loopers.domain.coupon.CouponUsageType;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.infrastructure.product.ProductEntity;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import java.time.ZonedDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 동시성 문제를 재현하는 테스트
 * read-modify-write 패턴에서 발생하는 Lost Update 문제를 검증합니다.
 */
@SpringBootTest
class ConcurrencyTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponTemplateService couponTemplateService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long brandId;
    private Long productId;

    @BeforeEach
    void setUp() {
        // 브랜드 생성
        Brand brand = brandService.create("테스트 브랜드", "테스트 설명");
        brandId = brand.getId();

        // 상품 생성 (좋아요 0, 재고 10)
        Product product = Product.create(null, "테스트 상품", brandId, 10000, 10, 0);
        ProductEntity productEntity = ProductEntity.create(product);
        productId = productJpaRepository.save(productEntity).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("좋아요 카운터 동시성 테스트")
    class LikeCountConcurrencyTest {

        @Test
        @DisplayName("동시에 100명이 좋아요를 눌러도 원자적 UPDATE로 정확한 카운트 유지")
        void concurrency_atomic_update_test() throws InterruptedException {
            // Given
            int threadCount = 100; // 스레드 수를 더 줄여서 더 현실적인 결과 얻기
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1); // 모든 스레드가 동시에 시작하도록
            CountDownLatch finishLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        // 모든 스레드가 동시에 시작하도록 대기
                        startLatch.await();
                        productService.increaseLikeCount(productId);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                        e.printStackTrace();
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }
            
            // 모든 스레드가 준비될 때까지 충분히 대기
            Thread.sleep(200);
            // 동시에 시작
            startLatch.countDown();
            finishLatch.await();
            executor.shutdown();

            // Then
            Product product = productService.getById(productId);
            System.out.println("성공: " + successCount.get() + ", 실패: " + failureCount.get());
            System.out.println("예상: " + threadCount + ", 실제: " + product.getLikeCount());
            // 원자적 UPDATE로 인해 정확한 카운트 유지
            assertThat(product.getLikeCount()).isEqualTo(threadCount);
        }
    }

    @Nested
    @DisplayName("재고 차감 동시성 테스트")
    class StockConcurrencyTest {

        @Test
        @DisplayName("재고보다 많은 주문이 동시에 들어와도, 재고만큼만 성공하고 나머지는 재고 부족 에러를 받는다")
        void concurrency_pessimistic_lock_prevents_overselling() throws InterruptedException {
            // Given: 재고 10, 스레드 30개 (각 1개씩 주문)
            int stock = 10;
            int threadCount = 30;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch finishLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        productService.decreaseStock(productId, 1);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            Thread.sleep(100);
            startLatch.countDown();
            finishLatch.await();
            executor.shutdown();

            // Then
            Product product = productService.getById(productId);
            System.out.println("성공: " + successCount.get() + ", 실패: " + failureCount.get());
            System.out.println("예상 재고: 0, 실제 재고: " + product.getStock());

            assertThat(product.getStock()).isEqualTo(0);
            assertThat(successCount.get()).isEqualTo(stock);
            assertThat(failureCount.get()).isEqualTo(threadCount - stock);
        }
    }

    @Nested
    @DisplayName("쿠폰 중복 사용 동시성 테스트")
    class CouponDuplicateUsageTest {

        @Test
        @DisplayName(" 동일한 쿠폰으로 여러 기기에서 동시에 주문해도, 쿠폰은 단 한 번만 사용된다")
        void concurrency_optimistic_lock_prevents_duplicate_usage() throws InterruptedException {
            // Given
            Long userId = 1L;
            CouponTemplate template = couponTemplateService.create(
                "테스트 쿠폰", DiscountType.FIXED, 1000, 5000, ZonedDateTime.now().plusDays(7)
            );
            Coupon coupon = couponService.issue(userId, template);
            Long couponId = coupon.getId();

            int threadCount = 100;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch finishLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failureCount = new AtomicInteger(0);

            Money orderAmount = new Money(10000);

            // When
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        couponService.applyToOrder(couponId, userId, orderAmount);
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failureCount.incrementAndGet();
                    } finally {
                        finishLatch.countDown();
                    }
                });
            }

            Thread.sleep(100);
            startLatch.countDown();
            finishLatch.await();
            executor.shutdown();

            // Then
            Coupon usedCoupon = couponService.getById(couponId);
            System.out.println("성공: " + successCount.get() + ", 실패: " + failureCount.get());
            System.out.println("쿠폰 상태: " + usedCoupon.getUsageType());

            assertThat(usedCoupon.getUsageType()).isEqualTo(CouponUsageType.USED);
            assertThat(successCount.get()).isEqualTo(1);
            assertThat(failureCount.get()).isEqualTo(threadCount - 1);
        }
    }
}

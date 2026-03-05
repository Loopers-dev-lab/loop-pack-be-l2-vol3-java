package com.loopers.concurrency;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.address.UserAddress;
import com.loopers.domain.address.UserAddressRepository;
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
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.point.PointAccount;
import com.loopers.domain.point.PointAccountRepository;
import com.loopers.domain.point.PointService;
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
 *
 * [동시 시작 패턴]
 * startLatch(CountDownLatch(1))를 사용하여 모든 스레드가 동시에 출발하도록 보장한다.
 * - 각 스레드는 submit 후 startLatch.await()에서 대기
 * - 모든 스레드가 준비된 뒤 startLatch.countDown()으로 동시 출발
 * - startLatch 없이 submit만 하면 for 루프 순회 + 스레드 스케줄링 차이로
 *   수 밀리초의 시작 시차가 생겨 완벽한 동시 경합이 보장되지 않는다.
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
    private PointService pointService;

    @Autowired
    private PointAccountRepository pointAccountRepository;

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserAddressRepository userAddressRepository;

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
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    inventoryService.reserveAll(Map.of(product.getId(), 1));
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        startLatch.countDown();

        latch.await();
        executor.shutdown();

        // assert
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isZero();
    }

    // ===== 2. 쿠폰 동시 사용 (원자적 UPDATE) =====

    @Test
    @DisplayName("동일 쿠폰을 10명이 동시에 사용하면, 정확히 1명만 성공한다")
    void 쿠폰_동시_사용_원자적_UPDATE() throws InterruptedException {
        // arrange
        CouponTemplate template = couponTemplateRepository.save(
                CouponTemplate.create("테스트쿠폰", "설명", DiscountType.FIXED, 1000, null,
                        0, 100, 10,
                        ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30))
        );

        // 쿠폰 1장을 userId=1에게 발급 (스냅샷 포함)
        IssuedCoupon issuedCoupon = issuedCouponRepository.save(
                IssuedCoupon.create(template.getId(), 1L,
                        template.getName(), template.getDiscountType(),
                        template.getDiscountValue(), template.getMaxDiscountAmount()));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act — 10개 스레드가 동일 쿠폰을 동시에 사용 시도
        for (int i = 0; i < threadCount; i++) {
            long orderId = 100L + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    couponService.use(issuedCoupon.getId(), 1L, orderId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        startLatch.countDown();

        latch.await();
        executor.shutdown();

        // assert — 원자적 UPDATE(WHERE status='ISSUED')로 정확히 1명만 성공
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(9);
    }

    // ===== 3. 쿠폰 동시 발급 (비관적 락 — COUNT-INSERT 갭 방지) =====

    @Test
    @DisplayName("최대 5장 발급 가능한 쿠폰에 10명이 동시에 발급 요청하면, 정확히 5명만 성공한다")
    void 쿠폰_동시_발급_비관적_락() throws InterruptedException {
        // arrange — maxIssueCount=5, maxIssueCountPerUser=1
        CouponTemplate template = couponTemplateRepository.save(
                CouponTemplate.create("한정쿠폰", "설명", DiscountType.FIXED, 1000, null,
                        0, 5, 1,
                        ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30))
        );

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act — 서로 다른 userId가 동시에 발급 요청
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    couponService.issue(template.getId(), userId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        startLatch.countDown();

        latch.await();
        executor.shutdown();

        // assert — 비관적 락으로 정확히 5명만 발급 성공
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        long totalIssued = issuedCouponRepository.countByCouponTemplateId(template.getId());
        assertThat(totalIssued).isEqualTo(5);
    }

    // ===== 4. 좋아요 동시 요청 (원자적 UPDATE) =====

    @Test
    @DisplayName("10명이 동시에 같은 상품에 좋아요하면, likeCount가 정확히 10이다")
    void 좋아요_동시_요청_원자적_UPDATE() throws InterruptedException {
        // arrange
        Brand brand = createBrand();
        Product product = createProduct(brand.getId());
        inventoryRepository.save(Inventory.create(product.getId(), 100));

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        // act — 서로 다른 userId가 동시에 좋아요
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1L;
            executor.submit(() -> {
                try {
                    startLatch.await();
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

        startLatch.countDown();

        latch.await();
        executor.shutdown();

        // assert — 원자적 UPDATE로 모두 성공, likeCount 정확히 10
        assertThat(errors).isEmpty();
        assertThat(successCount.get()).isEqualTo(threadCount);

        Product updated = productRepository.findById(product.getId()).orElseThrow();
        assertThat(updated.getLikeCount()).isEqualTo(threadCount);
    }

    // ===== 5. 포인트 동시 사용 (원자적 UPDATE) =====

    @Test
    @DisplayName("잔액 10000 포인트인 계좌에 10명이 동시에 1000 포인트씩 사용하면, 정확히 10명 모두 성공하고 잔액은 0이다")
    void 포인트_동시_사용_원자적_UPDATE() throws InterruptedException {
        // arrange — userId=1 계좌에 10000 포인트 충전
        PointAccount account = pointService.createAccount(1L);
        pointService.charge(1L, 10000);

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // act — 10개 스레드가 동시에 1000 포인트 사용
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    pointService.use(1L, 1000);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        startLatch.countDown();

        latch.await();
        executor.shutdown();

        // assert — 원자적 UPDATE(WHERE balance >= amount)로 정확히 10명 모두 성공, 잔액 0
        assertThat(successCount.get()).isEqualTo(10);
        assertThat(failCount.get()).isEqualTo(0);

        PointAccount updated = pointAccountRepository.findByUserId(1L).orElseThrow();
        assertThat(updated.getBalance()).isZero();
    }

    // ===== 6. 주문 통합 동시성 (비관적 락 + 원자적 UPDATE) =====

    @Test
    @DisplayName("재고 5개 상품에 10명이 동시에 주문하면, 재고 부족으로 정확히 5명만 성공한다")
    void 주문_동시_생성_통합_동시성() throws InterruptedException {
        // arrange — 상품, 재고, 각 사용자별 주소/포인트 준비
        Brand brand = createBrand();
        Product product = createProduct(brand.getId());
        inventoryRepository.save(Inventory.create(product.getId(), 5));

        int threadCount = 10;

        // 각 사용자(userId 1~10)의 주소 및 포인트 계좌 생성
        long[] addressIds = new long[threadCount];
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1L;
            UserAddress address = userAddressRepository.save(
                    UserAddress.create(userId, "수령인" + userId, "010-0000-000" + i,
                            "06234", "서울시 강남구", i + "호"));
            addressIds[i] = address.getId();

            PointAccount account = pointService.createAccount(userId);
            pointService.charge(userId, 50000);
        }

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Exception> errors = Collections.synchronizedList(new ArrayList<>());

        // act — 10명이 동시에 같은 상품 1개씩 주문 (포인트 1000 사용, 쿠폰 없음)
        for (int i = 0; i < threadCount; i++) {
            long userId = i + 1L;
            long addressId = addressIds[i];
            executor.submit(() -> {
                try {
                    startLatch.await();
                    orderFacade.createOrder(
                            userId, "주문자" + userId, "010-0000-0000",
                            List.of(new OrderFacade.OrderItemCommand(product.getId(), 1)),
                            addressId,
                            null,   // 쿠폰 없음
                            1000,   // 포인트 1000 사용
                            "CREDIT_CARD");
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    errors.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        startLatch.countDown();

        latch.await();
        executor.shutdown();

        // assert — 재고 5개이므로 5명만 성공
        assertThat(successCount.get()).isEqualTo(5);
        assertThat(failCount.get()).isEqualTo(5);

        // 재고 검증: 모두 예약+확정 완료
        Inventory inventory = inventoryRepository.findByProductId(product.getId()).orElseThrow();
        assertThat(inventory.getAvailableQuantity()).isZero();
    }
}

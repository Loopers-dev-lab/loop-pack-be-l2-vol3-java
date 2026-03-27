package com.loopers.domain.coupon;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.order.OrderItemCommand;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.StockService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRegisterCommand;
import com.loopers.domain.user.UserService;
import com.loopers.support.enums.DiscountType;
import com.loopers.support.enums.UserCouponStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("주문-쿠폰 동시성 테스트")
class OrderCouponConcurrencyTest {

    @Autowired OrderFacade orderFacade;
    @Autowired CouponService couponService;
    @Autowired UserCouponRepository userCouponRepository;
    @Autowired UserService userService;
    @Autowired ProductService productService;
    @Autowired BrandService brandService;
    @Autowired StockService stockService;
    @Autowired DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;
    private Long userCouponId;

    @BeforeEach
    void setUp() {
        // 브랜드 + 상품 + 재고 생성
        BrandModel brand = brandService.createBrand("테스트브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("테스트상품", brand.getBrandId(),
                BigDecimal.valueOf(50000), "설명");
        productId = product.getProductId();
        stockService.createStock(productId, 100);

        // 사용자 등록
        UserModel user = userService.register(new UserRegisterCommand(
                "couponuser", "Test1234!@#", "쿠폰유저",
                "19900101", "coupon@test.com", "서울"));
        userId = user.getUserId();

        // 쿠폰 생성 및 발급
        CouponModel coupon = couponService.createCoupon("테스트쿠폰", DiscountType.FIXED,
                BigDecimal.valueOf(5000), null, LocalDateTime.now().plusDays(30), null);
        UserCouponModel userCoupon = couponService.issueCoupon(userId, coupon.getCouponId());
        userCouponId = userCoupon.getUserCouponId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("2 스레드가 동일 쿠폰으로 동시 주문 시 1 성공, 1 실패, 쿠폰 USED")
    void concurrentOrderWithSameCoupon_ShouldSucceedOnlyOnce() throws InterruptedException {
        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    List<OrderItemCommand> items = List.of(new OrderItemCommand(productId, 1));
                    orderFacade.createDirectOrder(userId, items, userCouponId);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        UserCouponModel userCoupon = userCouponRepository.findById(userCouponId).get();
        assertThat(userCoupon.getStatus()).isEqualTo(UserCouponStatus.USED);
    }
}

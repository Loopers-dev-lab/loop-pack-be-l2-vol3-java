package com.loopers.domain.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponTemplateInfo;
import com.loopers.application.order.CreateOrderItemParam;
import com.loopers.application.order.OrderFacade;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductService;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * OrderFacade 트랜잭션 내에서 CouponService.validateAndUseInNewTransaction의 OptimisticLockException이
 * REQUIRES_NEW 트랜잭션에만 영향을 미치고, 부모 주문 트랜잭션은 롤백되지 않는지 검증한다.
 */
@SpringBootTest
@Import(MySqlTestContainersConfig.class)
class CouponPropagationIntegrationTest {

    @Autowired
    private OrderFacade orderFacade;
    @SpyBean
    private CouponService couponService;
    @Autowired
    private CouponFacade couponFacade;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductService productService;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;
    private Long issuedCouponId;

    @BeforeEach
    void setUp() {
        Long brandId = brandService.registerBrand("쿠폰전파-브랜드").getId();
        productId = productService.registerProduct(brandId, "쿠폰전파-상품", new BigDecimal("50000"), 10).getId();
        CouponTemplateInfo template = couponFacade.registerTemplate(
                "전파테스트 쿠폰", "FIXED", 1000, BigDecimal.valueOf(10000),
                ZonedDateTime.now().plusDays(30));
        userId = 999L;
        issuedCouponId = couponFacade.issueCoupon(userId, template.id()).id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("OrderFacade.placeOrder 내 첫 쿠폰 사용 시도에서 OptimisticLockException 발생해도 재시도 후 주문 트랜잭션은 커밋된다.")
    @Test
    void placeOrder_withCoupon_firstAttemptOptimisticLock_shouldRetryInNewTransactionAndCommitOrder() {
        AtomicInteger calls = new AtomicInteger();

        doAnswer(invocation -> {
            int attempt = calls.getAndIncrement();
            if (attempt == 0) {
                throw new OptimisticLockException("version conflict");
            }
            // 두 번째 호출부터는 실제 REQUIRES_NEW 메서드를 실행
            return invocation.callRealMethod();
        }).when(couponService).validateAndUseInNewTransaction(anyLong(), anyLong(), any());

        // when: OrderFacade.placeOrder 호출 (부모 @Transactional 내에서 쿠폰 사용 + 주문 생성)
        orderFacade.placeOrder(
                userId,
                List.of(new CreateOrderItemParam(productId, 1, null)),
                issuedCouponId
        );

        // then: REQUIRES_NEW 트랜잭션은 첫 번째 실패 후 두 번째 시도에서 성공해야 하고,
        // 부모 주문 트랜잭션은 UnexpectedRollbackException 없이 커밋되어 주문·쿠폰 상태가 반영된다.
        assertThat(calls.get()).isEqualTo(2);

        var myCoupons = couponService.findByUserId(userId, PageRequest.of(0, 10)).getContent();
        assertThat(myCoupons).hasSize(1);
        assertThat(myCoupons.get(0).getStatus()).isEqualTo(IssuedCouponStatus.USED);
    }
}


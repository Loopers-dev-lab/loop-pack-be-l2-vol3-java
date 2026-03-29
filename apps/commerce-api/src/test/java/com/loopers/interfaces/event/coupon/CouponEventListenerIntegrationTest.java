package com.loopers.interfaces.event.coupon;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.application.order.PlaceOrderCommand;
import com.loopers.application.order.PlaceOrderCommand.OrderItemCommand;
import com.loopers.application.order.PlaceOrderResult;
import com.loopers.application.order.PlaceOrderUseCase;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTerms;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.coupon.OwnedCoupon;
import com.loopers.domain.coupon.OwnedCouponFixture;
import com.loopers.domain.coupon.OwnedCouponRepository;
import com.loopers.domain.coupon.OwnedCouponService;
import com.loopers.domain.order.OrderService;
import com.loopers.support.BaseIntegrationTest;

class CouponEventListenerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private OwnedCouponRepository ownedCouponRepository;

    private Long brandId;
    private Long productId;

    @BeforeEach
    void setUp() {
        brandId = initDefaultBrand();
        productId = createProduct(brandId, "상품", 10000L, 100L);
    }

    @DisplayName("주문 생성 이벤트가 발행되면,")
    @Nested
    class OrderPlacedEvent {

        @DisplayName("쿠폰이 적용된 주문이면, 쿠폰이 사용 처리된다.")
        @Test
        void usesCoupon_whenOrderPlacedWithCoupon() {
            // arrange
            Long ownedCouponId = issueFixedCoupon(1L, 5000L);
            PlaceOrderCommand command = new PlaceOrderCommand(
                    1L,
                    List.of(new OrderItemCommand(productId, 2L)),
                    ownedCouponId
            );

            // act
            placeOrderUseCase.execute(command);

            // assert — AFTER_COMMIT + @Async이므로 비동기 대기
            await().atMost(5, SECONDS).untilAsserted(() -> {
                OwnedCoupon ownedCoupon = ownedCouponRepository.findByIdWithCoupon(ownedCouponId).orElseThrow();
                assertThat(ownedCoupon.getUsedAt()).isNotNull();
            });
        }

        @DisplayName("쿠폰 없이 주문하면, 쿠폰 관련 처리가 발생하지 않는다.")
        @Test
        void doesNothing_whenOrderPlacedWithoutCoupon() {
            // arrange
            Long ownedCouponId = issueFixedCoupon(1L, 5000L);
            PlaceOrderCommand command = new PlaceOrderCommand(
                    1L,
                    List.of(new OrderItemCommand(productId, 2L)),
                    null
            );

            // act
            placeOrderUseCase.execute(command);

            // assert
            OwnedCoupon ownedCoupon = ownedCouponRepository.findByIdWithCoupon(ownedCouponId).orElseThrow();
            assertThat(ownedCoupon.getUsedAt()).isNull();
        }
    }

    @DisplayName("주문 실패 이벤트가 발행되면,")
    @Nested
    class OrderFailedEvent {

        @DisplayName("사용된 쿠폰이 복원된다.")
        @Test
        void restoresCoupon_whenOrderFailed() {
            // arrange
            Long ownedCouponId = issueFixedCoupon(1L, 5000L);
            PlaceOrderCommand command = new PlaceOrderCommand(
                    1L,
                    List.of(new OrderItemCommand(productId, 2L)),
                    ownedCouponId
            );
            PlaceOrderResult result = placeOrderUseCase.execute(command);

            // 쿠폰 사용 처리 대기
            await().atMost(5, SECONDS).untilAsserted(() -> {
                OwnedCoupon ownedCoupon = ownedCouponRepository.findByIdWithCoupon(ownedCouponId).orElseThrow();
                assertThat(ownedCoupon.getUsedAt()).isNotNull();
            });

            // act
            orderService.fail(result.orderId());

            // assert — AFTER_COMMIT + @Async이므로 비동기 대기
            await().atMost(5, SECONDS).untilAsserted(() -> {
                OwnedCoupon ownedCoupon = ownedCouponRepository.findByIdWithCoupon(ownedCouponId).orElseThrow();
                assertThat(ownedCoupon.getUsedAt()).isNull();
            });
        }
    }

    private Long issueFixedCoupon(Long userId, Long discountValue) {
        var coupon = couponService.create(new CouponTerms(
                "테스트 쿠폰", CouponType.FIXED, discountValue, null, 1000L, ZonedDateTime.now().plusDays(30), 10000
        ));
        OwnedCoupon ownedCoupon = ownedCouponRepository.save(OwnedCouponFixture.createOwnedCoupon(coupon, userId));
        return ownedCoupon.getId();
    }
}

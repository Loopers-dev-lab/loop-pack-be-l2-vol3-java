package com.loopers.application.payment;

import com.loopers.application.order.CreateOrderCommand;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDomainService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponDomainService;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.domain.coupon.CouponIssueDomainService;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentGatewayException;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductDomainService;
import com.loopers.domain.product.Stock;
import com.loopers.domain.stock.ProductStockDomainService;
import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest
@DisplayName("재결제 재고/쿠폰 재선점 통합 테스트")
class PaymentRetryStockIntegrationTest {

    @Autowired
    private PaymentApplicationService paymentApplicationService;

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private OrderDomainService orderDomainService;

    @Autowired
    private ProductDomainService productDomainService;

    @Autowired
    private ProductStockDomainService productStockDomainService;

    @Autowired
    private BrandDomainService brandDomainService;

    @Autowired
    private CouponDomainService couponDomainService;

    @Autowired
    private CouponIssueDomainService couponIssueDomainService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @MockBean
    private PaymentGateway paymentGateway;

    private Long productId;

    @BeforeEach
    void setUp() {
        Brand brand = brandDomainService.register("나이키");
        Product product = productDomainService.register(brand.getId(), "에어맥스", 50000);
        productId = product.getId();
        productStockDomainService.create(productId, 10);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("재결제 재고 재선점 시, ")
    @Nested
    class RetryStockReservation {

        @DisplayName("1차 결제 실패 후 재고가 복원되고, 2차 재결제 시 다시 차감된다.")
        @Test
        void restoresAndReservesStock_whenRetryAfterFailure() {
            // 주문 생성 (재고 차감: 10 → 9)
            Order order = orderApplicationService.createOrder(
                new CreateOrderCommand(1L, List.of(new CreateOrderCommand.LineItem(productId, 1))));
            Long orderId = order.getId();
            assertThat(productStockDomainService.getByProductId(productId).getStock()).isEqualTo(new Stock(9));

            // 1차 결제
            when(paymentGateway.requestPayment(anyLong(), anyLong(), any(CardType.class), anyString(), anyInt()))
                .thenReturn("TR:first");
            paymentApplicationService.requestPayment(1L, orderId, CardType.SAMSUNG, "1234-5678-9012-3456");

            // FAILED 콜백 → 재고 복원 (9 → 10)
            paymentApplicationService.handleCallback("TR:first", "FAILED", "한도초과");
            assertThat(productStockDomainService.getByProductId(productId).getStock()).isEqualTo(new Stock(10));
            assertThat(orderDomainService.getById(orderId).getStatus()).isEqualTo(OrderStatus.PAYMENT_FAILED);

            // 2차 재결제 → 재고 재선점 (10 → 9)
            when(paymentGateway.requestPayment(anyLong(), anyLong(), any(CardType.class), anyString(), anyInt()))
                .thenReturn("TR:second");
            paymentApplicationService.requestPayment(1L, orderId, CardType.SAMSUNG, "1234-5678-9012-3456");
            assertThat(productStockDomainService.getByProductId(productId).getStock()).isEqualTo(new Stock(9));

            // 2차 결제 성공 → 재고 유지 (9)
            paymentApplicationService.handleCallback("TR:second", "SUCCESS", "정상 승인");
            assertThat(productStockDomainService.getByProductId(productId).getStock()).isEqualTo(new Stock(9));
        }
    }

    @DisplayName("재결제 쿠폰 재선점 시, ")
    @Nested
    class RetryCouponReservation {

        @DisplayName("1차 결제 실패 후 쿠폰이 AVAILABLE로 복원되고, 2차 재결제 시 다시 USED로 전환된다.")
        @Test
        void restoresAndReservesCoupon_whenRetryAfterFailure() {
            // 쿠폰 생성 + 발급
            Coupon coupon = couponDomainService.register(
                "5000원 할인", CouponType.FIXED, 5000, 10000, ZonedDateTime.now().plusDays(30));
            CouponIssue couponIssue = couponIssueDomainService.issue(coupon, 1L);
            Long couponIssueId = couponIssue.getId();

            // 쿠폰 적용 주문 생성 (재고 차감 + 쿠폰 USED)
            Order order = orderApplicationService.createOrder(
                new CreateOrderCommand(1L, List.of(new CreateOrderCommand.LineItem(productId, 1)), couponIssueId));
            Long orderId = order.getId();

            assertThat(couponIssueDomainService.getByIdAndUserId(couponIssueId, 1L).getStatus())
                .isEqualTo(CouponIssueStatus.USED);
            assertThat(productStockDomainService.getByProductId(productId).getStock()).isEqualTo(new Stock(9));

            // 1차 결제
            when(paymentGateway.requestPayment(anyLong(), anyLong(), any(CardType.class), anyString(), anyInt()))
                .thenReturn("TR:coupon-first");
            paymentApplicationService.requestPayment(1L, orderId, CardType.SAMSUNG, "1234-5678-9012-3456");

            // FAILED 콜백 → 쿠폰 AVAILABLE, 재고 복원
            paymentApplicationService.handleCallback("TR:coupon-first", "FAILED", "잔액 부족");
            assertThat(couponIssueDomainService.getByIdAndUserId(couponIssueId, 1L).getStatus())
                .isEqualTo(CouponIssueStatus.AVAILABLE);
            assertThat(productStockDomainService.getByProductId(productId).getStock()).isEqualTo(new Stock(10));

            // 2차 재결제 → 쿠폰 다시 USED, 재고 재선점
            when(paymentGateway.requestPayment(anyLong(), anyLong(), any(CardType.class), anyString(), anyInt()))
                .thenReturn("TR:coupon-second");
            paymentApplicationService.requestPayment(1L, orderId, CardType.SAMSUNG, "1234-5678-9012-3456");
            assertThat(couponIssueDomainService.getByIdAndUserId(couponIssueId, 1L).getStatus())
                .isEqualTo(CouponIssueStatus.USED);
            assertThat(productStockDomainService.getByProductId(productId).getStock()).isEqualTo(new Stock(9));

            // 2차 결제 성공 → 상태 유지
            paymentApplicationService.handleCallback("TR:coupon-second", "SUCCESS", "정상 승인");
            assertAll(
                () -> assertThat(couponIssueDomainService.getByIdAndUserId(couponIssueId, 1L).getStatus())
                    .isEqualTo(CouponIssueStatus.USED),
                () -> assertThat(productStockDomainService.getByProductId(productId).getStock())
                    .isEqualTo(new Stock(9))
            );
        }
    }

    @DisplayName("non-retryable PG 실패 시, ")
    @Nested
    class NonRetryableFailureRestoration {

        @DisplayName("재고가 즉시 복원된다.")
        @Test
        void restoresStock_whenPgRejectsRequest() {
            // 주문 생성 (재고 차감: 10 → 9)
            Order order = orderApplicationService.createOrder(
                new CreateOrderCommand(1L, List.of(new CreateOrderCommand.LineItem(productId, 1))));
            Long orderId = order.getId();
            assertThat(productStockDomainService.getByProductId(productId).getStock()).isEqualTo(new Stock(9));

            // PG가 명확히 거절
            when(paymentGateway.requestPayment(anyLong(), anyLong(), any(CardType.class), anyString(), anyInt()))
                .thenThrow(new PaymentGatewayException("잘못된 카드 정보"));

            // non-retryable 실패 → cancelPendingPayment → 재고 복원 (9 → 10)
            assertThrows(CoreException.class,
                () -> paymentApplicationService.requestPayment(1L, orderId, CardType.SAMSUNG, "1234-5678-9012-3456"));

            assertAll(
                () -> assertThat(productStockDomainService.getByProductId(productId).getStock())
                    .isEqualTo(new Stock(10)),
                () -> assertThat(orderDomainService.getById(orderId).getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_FAILED)
            );
        }
    }
}

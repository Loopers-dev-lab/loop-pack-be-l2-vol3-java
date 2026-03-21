package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderDomainService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.FakePaymentGateway;
import com.loopers.domain.payment.FakePaymentRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentDomainService;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.product.Money;
import com.loopers.domain.coupon.CouponIssueDomainService;
import com.loopers.domain.stock.ProductStockDomainService;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.ErrorType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PaymentApplicationServiceTest {

    private FakePaymentGateway fakeGateway;
    private FakePaymentRepository fakePaymentRepo;
    private PaymentDomainService paymentDomainService;
    private StubOrderDomainService stubOrderService;

    // PaymentTransactionHelper를 직접 호출하지 않고, 테스트에서는 트랜잭션 없이 동일 로직을 실행
    private PaymentApplicationService paymentApplicationService;

    @BeforeEach
    void setUp() {
        fakeGateway = new FakePaymentGateway();
        fakePaymentRepo = new FakePaymentRepository();
        paymentDomainService = new PaymentDomainService(fakePaymentRepo);
        stubOrderService = new StubOrderDomainService();

        // 단위 테스트에서는 TransactionHelper를 직접 생성 (트랜잭션 없이 동작)
        // 재고/쿠폰 복원은 FAILED 경로에서만 호출됨. Order에 items가 없으므로 빈 리스트 순회.
        ProductStockDomainService stockService = new ProductStockDomainService(null);
        CouponIssueDomainService couponService = new CouponIssueDomainService(null);
        PaymentTransactionHelper txHelper = new PaymentTransactionHelper(
            paymentDomainService, stubOrderService, stockService, couponService
        );

        paymentApplicationService = new PaymentApplicationService(
            txHelper, paymentDomainService, fakeGateway
        );
    }

    @DisplayName("결제 요청 시, ")
    @Nested
    class RequestPayment {

        @DisplayName("PG 요청 성공 시, IN_PROGRESS 상태의 Payment를 반환한다.")
        @Test
        void returnsInProgressPayment_whenPgSucceeds() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willSucceed("20250316:TR:abc123");

            Payment result = paymentApplicationService.requestPayment(
                1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456"
            );

            assertAll(
                () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS),
                () -> assertThat(result.getTransactionKey()).isEqualTo("20250316:TR:abc123"),
                () -> assertThat(result.getAmount()).isEqualTo(50000),
                () -> assertThat(stubOrderService.getLastOrder().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_PENDING)
            );
        }

        @DisplayName("PG 일시적 장애(timeout/5xx) 시, PENDING 상태의 Payment를 정상 반환한다.")
        @Test
        void returnsPendingPayment_whenPgRetryableFails() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willFail(); // retryable exception (timeout/5xx)

            Payment result = paymentApplicationService.requestPayment(
                1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456"
            );

            assertAll(
                () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING),
                () -> assertThat(result.getAmount()).isEqualTo(50000),
                () -> assertThat(stubOrderService.getLastOrder().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_PENDING)
            );
        }

        @DisplayName("PG 비재시도 장애(4xx/계약 오류) 시, CoreException이 발생하고 Payment/Order가 즉시 FAILED로 전환된다.")
        @Test
        void throwsExceptionAndFailsPayment_whenPgNonRetryableFails() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willFailNonRetryable();

            CoreException result = assertThrows(CoreException.class,
                () -> paymentApplicationService.requestPayment(
                    1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456"
                ));

            assertAll(
                () -> assertThat(result.getMessage()).contains("PG 요청에 실패했습니다"),
                () -> assertThat(stubOrderService.getLastOrder().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_FAILED),
                () -> {
                    List<Payment> payments = paymentDomainService.getByOrderId(1L);
                    assertThat(payments).hasSize(1);
                    assertThat(payments.get(0).getStatus()).isEqualTo(PaymentStatus.FAILED);
                }
            );
        }

        @DisplayName("같은 주문에 대해 이미 결제가 진행 중이면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenDuplicatePayment() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willSucceed("20250316:TR:abc123");

            // 첫 번째 결제 요청 → 성공
            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

            // 같은 주문에 대해 두 번째 결제 요청 → CONFLICT
            CoreException result = assertThrows(CoreException.class,
                () -> paymentApplicationService.requestPayment(
                    1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456"
                ));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("이전 결제가 FAILED 상태이면, 같은 주문에 대해 재결제가 가능하다.")
        @Test
        void allowsNewPayment_whenPreviousFailed() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willSucceed("20250316:TR:abc123");

            // 첫 번째 결제 → 성공 후 FAILED 콜백
            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");
            paymentApplicationService.handleCallback("20250316:TR:abc123", "FAILED", "한도초과");

            // 같은 주문에 대해 재결제 (PAYMENT_FAILED → PAYMENT_PENDING)
            fakeGateway.willSucceed("20250316:TR:def456");

            Payment result = paymentApplicationService.requestPayment(
                1L, 1L, CardType.KB, "1234-5678-9012-3456"
            );

            assertAll(
                () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS),
                () -> assertThat(result.getTransactionKey()).isEqualTo("20250316:TR:def456"),
                () -> assertThat(stubOrderService.getLastOrder().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_PENDING)
            );
        }

        @DisplayName("PG 타임아웃 시, PENDING 상태의 Payment를 정상 반환한다.")
        @Test
        void returnsPendingPayment_whenPgTimeout() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willTimeout();

            Payment result = paymentApplicationService.requestPayment(
                1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456"
            );

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }
    }

    @DisplayName("콜백 수신 시, ")
    @Nested
    class HandleCallback {

        @DisplayName("SUCCESS 콜백이면, Payment는 PAID, Order는 PAID가 된다.")
        @Test
        void marksPaidOnSuccessCallback() {
            // 준비: Payment를 IN_PROGRESS 상태로 만들기
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willSucceed("20250316:TR:abc123");
            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

            // 콜백 처리
            paymentApplicationService.handleCallback("20250316:TR:abc123", "SUCCESS", "정상 승인");

            Payment payment = paymentDomainService.getByTransactionKey("20250316:TR:abc123");
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID),
                () -> assertThat(stubOrderService.getLastOrder().getStatus())
                    .isEqualTo(OrderStatus.PAID)
            );
        }

        @DisplayName("FAILED 콜백이면, Payment는 FAILED, Order는 PAYMENT_FAILED가 된다.")
        @Test
        void marksFailedOnFailedCallback() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willSucceed("20250316:TR:abc123");
            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

            paymentApplicationService.handleCallback("20250316:TR:abc123", "FAILED", "한도초과입니다.");

            Payment payment = paymentDomainService.getByTransactionKey("20250316:TR:abc123");
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(payment.getFailureReason()).isEqualTo("한도초과입니다."),
                () -> assertThat(stubOrderService.getLastOrder().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_FAILED)
            );
        }

        @DisplayName("이미 FAILED인 결제에 콜백이 오면, 멱등하게 처리한다.")
        @Test
        void idempotent_whenAlreadyFailed() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willSucceed("20250316:TR:abc123");
            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");
            paymentApplicationService.handleCallback("20250316:TR:abc123", "FAILED", "한도초과");

            // 중복 FAILED 콜백 - 예외 없이 처리
            paymentApplicationService.handleCallback("20250316:TR:abc123", "FAILED", "한도초과");

            Payment payment = paymentDomainService.getByTransactionKey("20250316:TR:abc123");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.FAILED);
        }

        @DisplayName("이미 PAID인 결제에 콜백이 오면, 멱등하게 처리한다.")
        @Test
        void idempotent_whenAlreadyPaid() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willSucceed("20250316:TR:abc123");
            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");
            paymentApplicationService.handleCallback("20250316:TR:abc123", "SUCCESS", "정상 승인");

            // 중복 콜백 - 예외 없이 처리
            paymentApplicationService.handleCallback("20250316:TR:abc123", "SUCCESS", "정상 승인");

            Payment payment = paymentDomainService.getByTransactionKey("20250316:TR:abc123");
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @DisplayName("알 수 없는 상태 콜백이면, 상태 전이 없이 IN_PROGRESS를 유지한다.")
        @Test
        void doesNotChangeState_whenUnknownCallbackStatus() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willSucceed("20250316:TR:abc123");
            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

            // 알 수 없는 상태로 콜백
            paymentApplicationService.handleCallback("20250316:TR:abc123", "PROCESSING", null);

            Payment payment = paymentDomainService.getByTransactionKey("20250316:TR:abc123");
            assertAll(
                () -> assertThat(payment.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS),
                () -> assertThat(stubOrderService.getLastOrder().getStatus())
                    .isEqualTo(OrderStatus.PAYMENT_PENDING)
            );
        }
    }

    @DisplayName("상태 동기화 시, ")
    @Nested
    class SyncPaymentStatus {

        @DisplayName("PG에서 SUCCESS이면, Payment를 PAID로 업데이트한다.")
        @Test
        void updatesToPaid_whenPgReturnsSuccess() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willSucceed("20250316:TR:abc123");
            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

            // PG 조회 결과 설정
            fakeGateway.setNextTransactionResult(
                new PaymentGateway.TransactionResult("20250316:TR:abc123", "SUCCESS", "정상 승인")
            );

            Payment result = paymentApplicationService.syncPaymentStatus(1L, "20250316:TR:abc123");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @DisplayName("PG에서 PENDING이면, 상태 변경 없이 반환한다.")
        @Test
        void noChange_whenPgReturnsPending() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willSucceed("20250316:TR:abc123");
            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

            fakeGateway.setNextTransactionResult(
                new PaymentGateway.TransactionResult("20250316:TR:abc123", "PENDING", null)
            );

            Payment result = paymentApplicationService.syncPaymentStatus(1L, "20250316:TR:abc123");

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
        }

        @DisplayName("이미 PAID 상태이면, PG 조회 없이 반환한다.")
        @Test
        void returnDirectly_whenAlreadyPaid() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willSucceed("20250316:TR:abc123");
            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");
            paymentApplicationService.handleCallback("20250316:TR:abc123", "SUCCESS", "정상 승인");

            // PG 타임아웃으로 설정해도, 이미 PAID이면 PG 호출하지 않아야 함
            fakeGateway.willTimeout();

            Payment result = paymentApplicationService.syncPaymentStatus(1L, "20250316:TR:abc123");
            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
        }

        @DisplayName("다른 유저의 결제를 조회하면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenDifferentUser() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willSucceed("20250316:TR:abc123");
            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

            assertThrows(CoreException.class,
                () -> paymentApplicationService.syncPaymentStatus(999L, "20250316:TR:abc123"));
        }
    }

    @DisplayName("orderId 기반 PENDING 복구 시, ")
    @Nested
    class SyncByOrderId {

        @DisplayName("PG 1건이면, PENDING Payment를 복구한다.")
        @Test
        void recoversPendingPayment_whenPgHasOneTransaction() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willFail(); // PG 일시적 장애 → PENDING fallback

            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

            // PG에서 실제로는 접수되었다고 가정 (Read Timeout 시나리오)
            fakeGateway.setNextOrderResults(List.of(
                new PaymentGateway.TransactionResult("20250316:TR:recovered", "SUCCESS", "정상 승인")
            ));

            Payment result = paymentApplicationService.syncByOrderId(1L, 1L);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.PAID);
            assertThat(result.getTransactionKey()).isEqualTo("20250316:TR:recovered");
        }

        @DisplayName("PG 정상 0건이면, PENDING Payment를 FAILED로 처리한다.")
        @Test
        void failsPendingPayment_whenPgConfirmsZeroTransactions() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willFail(); // retryable → PENDING fallback

            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

            fakeGateway.setNextOrderResults(List.of()); // PG 정상 응답 0건 → 미접수 확정

            Payment result = paymentApplicationService.syncByOrderId(1L, 1L);

            assertAll(
                () -> assertThat(result.getStatus()).isEqualTo(PaymentStatus.FAILED),
                () -> assertThat(result.getFailureReason()).isEqualTo("PG 미접수 확인됨")
            );
        }

        @DisplayName("PG 2건 이상이면, INTERNAL_ERROR 예외가 발생한다 (이중 결제 사고).")
        @Test
        void throwsInternalError_whenPgHasMultipleTransactions() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willFail(); // retryable → PENDING fallback

            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

            fakeGateway.setNextOrderResults(List.of(
                new PaymentGateway.TransactionResult("20250316:TR:first", "SUCCESS", "정상 승인"),
                new PaymentGateway.TransactionResult("20250316:TR:second", "SUCCESS", "정상 승인")
            ));

            CoreException result = assertThrows(CoreException.class,
                () -> paymentApplicationService.syncByOrderId(1L, 1L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
        }

        @DisplayName("PENDING 결제가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNoPendingPayment() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willSucceed("20250316:TR:abc123");
            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");
            // IN_PROGRESS 상태 → PENDING 아님

            CoreException result = assertThrows(CoreException.class,
                () -> paymentApplicationService.syncByOrderId(1L, 1L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("PG가 아직 PENDING이면, IN_PROGRESS까지만 전환한다.")
        @Test
        void transitionsToInProgressOnly_whenPgStillPending() {
            stubOrderService.setOrder(new Order(1L, new Money(50000)));
            fakeGateway.willFail(); // retryable → PENDING fallback

            paymentApplicationService.requestPayment(1L, 1L, CardType.SAMSUNG, "1234-5678-9012-3456");

            fakeGateway.setNextOrderResults(List.of(
                new PaymentGateway.TransactionResult("20250316:TR:pending", "PENDING", null)
            ));

            Payment result = paymentApplicationService.syncByOrderId(1L, 1L);

            assertThat(result.getStatus()).isEqualTo(PaymentStatus.IN_PROGRESS);
            assertThat(result.getTransactionKey()).isEqualTo("20250316:TR:pending");
        }
    }

    /**
     * OrderDomainService 테스트 대역.
     * 순수 클래스이므로 FakeRepository와 함께 사용하기보다,
     * 간단한 Stub으로 필요한 동작만 제공한다.
     */
    static class StubOrderDomainService extends OrderDomainService {
        private Order order;

        StubOrderDomainService() {
            super(null); // Repository는 사용하지 않음
        }

        void setOrder(Order order) {
            this.order = order;
        }

        Order getLastOrder() {
            return order;
        }

        @Override
        public Order getByIdAndUserId(Long id, Long userId) {
            return order;
        }

        @Override
        public Order getByIdAndUserIdForUpdate(Long id, Long userId) {
            return order;
        }

        @Override
        public Order getById(Long id) {
            return order;
        }

        @Override
        public Order getByIdWithItems(Long id) {
            return order;
        }
    }
}

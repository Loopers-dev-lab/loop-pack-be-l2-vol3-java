package com.loopers.application.payment;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.order.Order;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.fake.*;
import com.loopers.infrastructure.pg.PgPaymentStatusResponse;
import com.loopers.infrastructure.pg.PgRouter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentFacadeTest {

    private PaymentFacade paymentFacade;
    private FakePaymentRepository paymentRepository;
    private FakeOrderRepository orderRepository;
    private FakePaymentOutboxRepository outboxRepository;
    private FakePgClient primaryPgClient;
    private PgRouter pgRouter;

    @BeforeEach
    void setUp() throws Exception {
        paymentRepository = new FakePaymentRepository();
        orderRepository = new FakeOrderRepository();
        outboxRepository = new FakePaymentOutboxRepository();
        primaryPgClient = new FakePgClient("SIMULATOR");
        pgRouter = new PgRouter(List.of(primaryPgClient));

        paymentFacade = new PaymentFacade(paymentRepository, orderRepository, pgRouter, outboxRepository);

        // @Value 필드 주입 (Spring 컨텍스트 없이)
        setField(paymentFacade, "callbackUrl", "http://test/callback");
        setField(paymentFacade, "maxRetryAttempts", 3);
        setField(paymentFacade, "initialWaitMs", 0L);
        setField(paymentFacade, "backoffMultiplier", 2);
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private Order createTestOrder(Long memberId) {
        FakeBrandRepository brandRepository = new FakeBrandRepository();
        Brand brand = brandRepository.save(new Brand("나이키", "스포츠 브랜드"));

        Order order = Order.create(memberId, List.of(
            new Order.ItemSnapshot(1L, "에어맥스", 5000, brand.getName(), 1)
        ));
        return orderRepository.save(order);
    }

    @Nested
    @DisplayName("결제 요청")
    class RequestPayment {

        @DisplayName("U1-8: 정상 결제 요청 → PENDING 응답")
        @Test
        void requestPayment_success_returnsPending() {
            Order order = createTestOrder(1L);

            PaymentFacade.PaymentResult result = paymentFacade.requestPayment(
                order.getId(), "SAMSUNG", "1234-5678-9012-3456", 5000);

            assertThat(result.paymentId()).isNotNull();
            assertThat(result.transactionKey()).isNotNull();
            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.failureReason()).isNull();

            PaymentModel saved = paymentRepository.findById(result.paymentId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(saved.getTransactionKey()).isEqualTo(result.transactionKey());
            assertThat(saved.getOrderId()).isEqualTo(order.getId());
            assertThat(saved.getPgProvider()).isEqualTo("SIMULATOR");
        }

        @DisplayName("U1-9: 주문 없음 → 예외")
        @Test
        void requestPayment_orderNotFound_throwsException() {
            assertThatThrownBy(() -> paymentFacade.requestPayment(
                999L, "SAMSUNG", "1234-5678-9012-3456", 5000))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("U1-10: 이미 결제된 주문 → 예외")
        @Test
        void requestPayment_alreadyPaid_throwsException() {
            Order order = createTestOrder(1L);

            paymentFacade.requestPayment(
                order.getId(), "SAMSUNG", "1234-5678-9012-3456", 5000);

            assertThatThrownBy(() -> paymentFacade.requestPayment(
                order.getId(), "SAMSUNG", "1234-5678-9012-3456", 5000))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("취소된 주문에 대한 결제 요청 → 예외")
        @Test
        void requestPayment_cancelledOrder_throwsException() {
            Order order = createTestOrder(1L);
            order.cancel();

            assertThatThrownBy(() -> paymentFacade.requestPayment(
                order.getId(), "SAMSUNG", "1234-5678-9012-3456", 5000))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("수동 Retry + UNKNOWN Fallback")
    class RetryAndFallback {

        @DisplayName("U2-4: PG 1차 실패 → PG에 기록 있음 → 재시도 안 함 (멱등성)")
        @Test
        void requestPayment_pgHasExistingRecord_noRetry() {
            Order order = createTestOrder(1L);
            primaryPgClient.setFailCount(1);

            // PG에 이미 PENDING 기록 등록 (네트워크 실패했지만 PG는 처리 완료한 상황)
            primaryPgClient.registerOrderStatus(String.valueOf(order.getId()),
                new PgPaymentStatusResponse("PENDING", "TX-EXISTING-123", null));

            PaymentFacade.PaymentResult result = paymentFacade.requestPayment(
                order.getId(), "SAMSUNG", "1234-5678-9012-3456", 5000);

            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.transactionKey()).isEqualTo("TX-EXISTING-123");
            // PG requestPayment는 1회만 호출됨 (2차 시도 없이 기존 기록으로 완료)
            assertThat(primaryPgClient.getCallCount()).isEqualTo(1);

            PaymentModel saved = paymentRepository.findById(result.paymentId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
            assertThat(saved.getTransactionKey()).isEqualTo("TX-EXISTING-123");
        }

        @DisplayName("U2-5: PG 1차 실패 → PG 기록 없음 → 재시도 → 성공")
        @Test
        void requestPayment_pgNoRecord_retrySuccess() {
            Order order = createTestOrder(1L);
            primaryPgClient.setFailCount(1);

            PaymentFacade.PaymentResult result = paymentFacade.requestPayment(
                order.getId(), "SAMSUNG", "1234-5678-9012-3456", 5000);

            assertThat(result.status()).isEqualTo("PENDING");
            assertThat(result.transactionKey()).isNotNull();
            // 1차 실패 + 2차 성공 = 2회 호출
            assertThat(primaryPgClient.getCallCount()).isEqualTo(2);

            PaymentModel saved = paymentRepository.findById(result.paymentId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PENDING);
        }

        @DisplayName("U2-6: 모든 PG 실패 → UNKNOWN 상태 저장 + '확인 중' 응답")
        @Test
        void requestPayment_allPgFail_unknownFallback() {
            Order order = createTestOrder(1L);
            primaryPgClient.setShouldFail(true);

            PaymentFacade.PaymentResult result = paymentFacade.requestPayment(
                order.getId(), "SAMSUNG", "1234-5678-9012-3456", 5000);

            assertThat(result.status()).isEqualTo("UNKNOWN");
            assertThat(result.failureReason()).contains("확인 중");
            assertThat(result.transactionKey()).isNull();

            PaymentModel saved = paymentRepository.findById(result.paymentId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo(PaymentStatus.UNKNOWN);
        }
    }

    @Nested
    @DisplayName("결제 조회")
    class GetPayment {

        @DisplayName("paymentId로 결제 조회 성공")
        @Test
        void getPayment_byId_success() {
            Order order = createTestOrder(1L);
            PaymentFacade.PaymentResult result = paymentFacade.requestPayment(
                order.getId(), "SAMSUNG", "1234-5678-9012-3456", 5000);

            PaymentModel payment = paymentFacade.getPayment(result.paymentId());

            assertThat(payment.getId()).isEqualTo(result.paymentId());
            assertThat(payment.getOrderId()).isEqualTo(order.getId());
        }

        @DisplayName("존재하지 않는 paymentId → 예외")
        @Test
        void getPayment_notFound_throwsException() {
            assertThatThrownBy(() -> paymentFacade.getPayment(999L))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("orderId로 결제 조회 성공")
        @Test
        void getPaymentByOrderId_success() {
            Order order = createTestOrder(1L);
            paymentFacade.requestPayment(
                order.getId(), "SAMSUNG", "1234-5678-9012-3456", 5000);

            PaymentModel payment = paymentFacade.getPaymentByOrderId(order.getId());

            assertThat(payment.getOrderId()).isEqualTo(order.getId());
        }
    }
}

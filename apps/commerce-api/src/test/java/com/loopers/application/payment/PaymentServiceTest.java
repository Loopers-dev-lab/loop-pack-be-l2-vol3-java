package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.infrastructure.pg.PgClient;
import com.loopers.infrastructure.pg.dto.PgPaymentRequest;
import com.loopers.infrastructure.pg.dto.PgPaymentResponse;
import com.loopers.infrastructure.pg.dto.PgPaymentStatus;
import com.loopers.infrastructure.pg.exception.PgClientException;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService 테스트")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PgClient pgClient;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService = new PaymentService(paymentRepository, orderRepository, pgClient);
    }

    @Nested
    @DisplayName("결제 요청 테스트")
    class RequestPayment {

        @Test
        @DisplayName("성공 - 결제 요청 후 PENDING 상태로 저장")
        void 결제_요청_성공() {
            // Arrange
            Long userId = 1L;
            Long orderId = 100L;
            String cardType = "SAMSUNG";
            String cardNo = "1234-5678-9814-1451";

            Order order = createTestOrder(orderId, userId, 50000L);
            given(orderRepository.findByIdAndUserId(orderId, userId)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            PgPaymentResponse pgResponse = new PgPaymentResponse(
                    "TXN-001", String.valueOf(orderId), PgPaymentStatus.PENDING,
                    50000L, cardType, cardNo,
                    ZonedDateTime.now(), ZonedDateTime.now(), null
            );
            given(pgClient.requestPayment(eq(userId), any(PgPaymentRequest.class))).willReturn(pgResponse);

            Payment savedPayment = Payment.reconstitute(
                    1L, orderId, "TXN-001", new Money(50000L),
                    PaymentStatus.PENDING, null, ZonedDateTime.now(), ZonedDateTime.now()
            );
            given(paymentRepository.save(any(Payment.class))).willReturn(savedPayment);

            // Act
            PaymentResult result = paymentService.requestPayment(userId, orderId, cardType, cardNo);

            // Assert
            assertThat(result.paymentId()).isEqualTo(1L);
            assertThat(result.orderId()).isEqualTo(orderId);
            assertThat(result.transactionId()).isEqualTo("TXN-001");
            assertThat(result.amount()).isEqualTo(50000L);
            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING);

            ArgumentCaptor<PgPaymentRequest> requestCaptor = ArgumentCaptor.forClass(PgPaymentRequest.class);
            verify(pgClient).requestPayment(eq(userId), requestCaptor.capture());
            PgPaymentRequest capturedRequest = requestCaptor.getValue();
            assertThat(capturedRequest.orderId()).isEqualTo(String.valueOf(orderId));
            assertThat(capturedRequest.cardType()).isEqualTo(cardType);
            assertThat(capturedRequest.cardNo()).isEqualTo(cardNo);
            assertThat(capturedRequest.amount()).isEqualTo(50000L);
        }

        @Test
        @DisplayName("실패 - 주문을 찾을 수 없음")
        void 주문_없음_실패() {
            // Arrange
            Long userId = 1L;
            Long orderId = 100L;

            given(orderRepository.findByIdAndUserId(orderId, userId)).willReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> paymentService.requestPayment(userId, orderId, "SAMSUNG", "1234"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> {
                        CoreException ce = (CoreException) ex;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND);
                    });

            verify(pgClient, never()).requestPayment(anyLong(), any());
        }

        @Test
        @DisplayName("실패 - 이미 결제가 존재함")
        void 이미_결제_존재_실패() {
            // Arrange
            Long userId = 1L;
            Long orderId = 100L;

            Order order = createTestOrder(orderId, userId, 50000L);
            given(orderRepository.findByIdAndUserId(orderId, userId)).willReturn(Optional.of(order));

            Payment existingPayment = Payment.reconstitute(
                    1L, orderId, "TXN-001", new Money(50000L),
                    PaymentStatus.PENDING, null, ZonedDateTime.now(), ZonedDateTime.now()
            );
            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.of(existingPayment));

            // Act & Assert
            assertThatThrownBy(() -> paymentService.requestPayment(userId, orderId, "SAMSUNG", "1234"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> {
                        CoreException ce = (CoreException) ex;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.PAYMENT_ALREADY_EXISTS);
                    });

            verify(pgClient, never()).requestPayment(anyLong(), any());
        }

        @Test
        @DisplayName("실패 - PG 요청 실패 시 PgClientException 발생 (CircuitBreaker가 감지)")
        void PG_요청_실패() {
            // Arrange
            Long userId = 1L;
            Long orderId = 100L;

            Order order = createTestOrder(orderId, userId, 50000L);
            given(orderRepository.findByIdAndUserId(orderId, userId)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.empty());
            given(pgClient.requestPayment(eq(userId), any(PgPaymentRequest.class)))
                    .willThrow(PgClientException.connectionError(new RuntimeException("Connection refused")));

            // Act & Assert
            // 단위 테스트에서는 @CircuitBreaker가 적용되지 않으므로 PgClientException이 그대로 던져짐
            // 실제 런타임에서는 CircuitBreaker가 이를 감지하여 fallback 호출
            assertThatThrownBy(() -> paymentService.requestPayment(userId, orderId, "SAMSUNG", "1234"))
                    .isInstanceOf(PgClientException.class);

            verify(paymentRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("Fallback 테스트")
    class Fallback {

        @Test
        @DisplayName("CircuitBreaker OPEN 시 PENDING_RETRY 상태로 저장")
        void CircuitBreaker_OPEN_fallback() {
            // Arrange
            Long userId = 1L;
            Long orderId = 100L;

            Order order = createTestOrder(orderId, userId, 50000L);
            given(orderRepository.findByIdAndUserId(orderId, userId)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            Payment savedPayment = Payment.reconstitute(
                    1L, orderId, null, new Money(50000L),
                    PaymentStatus.PENDING_RETRY, "현재 결제 시스템이 불안정합니다. 잠시 후 다시 시도해주세요.",
                    ZonedDateTime.now(), ZonedDateTime.now()
            );
            given(paymentRepository.save(any(Payment.class))).willReturn(savedPayment);

            // CircuitBreaker 설정으로 실제 예외 생성
            io.github.resilience4j.circuitbreaker.CircuitBreakerConfig config =
                    io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.ofDefaults();
            io.github.resilience4j.circuitbreaker.CircuitBreaker circuitBreaker =
                    io.github.resilience4j.circuitbreaker.CircuitBreaker.of("test", config);
            io.github.resilience4j.circuitbreaker.CallNotPermittedException exception =
                    io.github.resilience4j.circuitbreaker.CallNotPermittedException
                            .createCallNotPermittedException(circuitBreaker);

            // Act
            PaymentResult result = paymentService.requestPaymentFallback(
                    userId, orderId, "SAMSUNG", "1234", exception
            );

            // Assert
            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING_RETRY);
            assertThat(result.errorMessage()).isEqualTo("현재 결제 시스템이 불안정합니다. 잠시 후 다시 시도해주세요.");
            assertThat(result.hasError()).isTrue();
        }

        @Test
        @DisplayName("Timeout 시 PENDING_RETRY 상태로 저장")
        void Timeout_fallback() {
            // Arrange
            Long userId = 1L;
            Long orderId = 100L;

            Order order = createTestOrder(orderId, userId, 50000L);
            given(orderRepository.findByIdAndUserId(orderId, userId)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            Payment savedPayment = Payment.reconstitute(
                    1L, orderId, null, new Money(50000L),
                    PaymentStatus.PENDING_RETRY, "결제 요청 처리 중 시간이 초과되었습니다.",
                    ZonedDateTime.now(), ZonedDateTime.now()
            );
            given(paymentRepository.save(any(Payment.class))).willReturn(savedPayment);

            java.util.concurrent.TimeoutException exception = new java.util.concurrent.TimeoutException("timeout");

            // Act
            PaymentResult result = paymentService.requestPaymentFallback(
                    userId, orderId, "SAMSUNG", "1234", exception
            );

            // Assert
            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING_RETRY);
            assertThat(result.errorMessage()).isEqualTo("결제 요청 처리 중 시간이 초과되었습니다.");
        }

        @Test
        @DisplayName("PG 통신 예외 시 PENDING_RETRY 상태로 저장")
        void PgClientException_fallback() {
            // Arrange
            Long userId = 1L;
            Long orderId = 100L;

            Order order = createTestOrder(orderId, userId, 50000L);
            given(orderRepository.findByIdAndUserId(orderId, userId)).willReturn(Optional.of(order));
            given(paymentRepository.findByOrderId(orderId)).willReturn(Optional.empty());

            Payment savedPayment = Payment.reconstitute(
                    1L, orderId, null, new Money(50000L),
                    PaymentStatus.PENDING_RETRY, "결제 처리 중 오류가 발생했습니다.",
                    ZonedDateTime.now(), ZonedDateTime.now()
            );
            given(paymentRepository.save(any(Payment.class))).willReturn(savedPayment);

            PgClientException exception = PgClientException.connectionError(new RuntimeException("connection error"));

            // Act
            PaymentResult result = paymentService.requestPaymentFallback(
                    userId, orderId, "SAMSUNG", "1234", exception
            );

            // Assert
            assertThat(result.status()).isEqualTo(PaymentStatus.PENDING_RETRY);
            assertThat(result.errorMessage()).isEqualTo("결제 처리 중 오류가 발생했습니다.");
        }

        @Test
        @DisplayName("CoreException은 fallback에서 다시 throw")
        void CoreException_rethrow() {
            // Arrange
            Long userId = 1L;
            Long orderId = 100L;

            CoreException coreException = new CoreException(ErrorType.ORDER_NOT_FOUND);

            // Act & Assert
            assertThatThrownBy(() -> paymentService.requestPaymentFallback(
                    userId, orderId, "SAMSUNG", "1234", coreException
            ))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> {
                        CoreException ce = (CoreException) ex;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.ORDER_NOT_FOUND);
                    });
        }

        @Test
        @DisplayName("주문을 찾을 수 없으면 에러 결과만 반환")
        void 주문_없음_에러_반환() {
            // Arrange
            Long userId = 1L;
            Long orderId = 100L;

            given(orderRepository.findByIdAndUserId(orderId, userId)).willReturn(Optional.empty());

            PgClientException exception = PgClientException.connectionError(new RuntimeException("error"));

            // Act
            PaymentResult result = paymentService.requestPaymentFallback(
                    userId, orderId, "SAMSUNG", "1234", exception
            );

            // Assert
            assertThat(result.paymentId()).isNull();
            assertThat(result.orderId()).isEqualTo(orderId);
            assertThat(result.errorMessage()).isEqualTo("결제 처리 중 오류가 발생했습니다.");
            assertThat(result.hasError()).isTrue();
        }
    }

    @Nested
    @DisplayName("콜백 처리 테스트")
    class HandleCallback {

        @Test
        @DisplayName("성공 - 결제 승인 콜백")
        void 결제_승인_콜백() {
            // Arrange
            String transactionId = "TXN-001";
            Long orderId = 100L;

            Payment pendingPayment = Payment.reconstitute(
                    1L, orderId, transactionId, new Money(50000L),
                    PaymentStatus.PENDING, null, ZonedDateTime.now(), ZonedDateTime.now()
            );
            given(paymentRepository.findByTransactionId(transactionId)).willReturn(Optional.of(pendingPayment));

            Payment successPayment = Payment.reconstitute(
                    1L, orderId, transactionId, new Money(50000L),
                    PaymentStatus.SUCCESS, null, ZonedDateTime.now(), ZonedDateTime.now()
            );
            given(paymentRepository.save(any(Payment.class))).willReturn(successPayment);

            PgPaymentResponse callbackData = new PgPaymentResponse(
                    transactionId, String.valueOf(orderId), PgPaymentStatus.APPROVED,
                    50000L, "SAMSUNG", "1234",
                    ZonedDateTime.now(), ZonedDateTime.now(), null
            );

            // Act
            PaymentResult result = paymentService.handleCallback(callbackData);

            // Assert
            assertThat(result.status()).isEqualTo(PaymentStatus.SUCCESS);
            verify(paymentRepository).save(any(Payment.class));
        }

        @Test
        @DisplayName("성공 - 결제 실패 콜백")
        void 결제_실패_콜백() {
            // Arrange
            String transactionId = "TXN-001";
            Long orderId = 100L;
            String failReason = "잔액 부족";

            Payment pendingPayment = Payment.reconstitute(
                    1L, orderId, transactionId, new Money(50000L),
                    PaymentStatus.PENDING, null, ZonedDateTime.now(), ZonedDateTime.now()
            );
            given(paymentRepository.findByTransactionId(transactionId)).willReturn(Optional.of(pendingPayment));

            Payment failedPayment = Payment.reconstitute(
                    1L, orderId, transactionId, new Money(50000L),
                    PaymentStatus.FAIL, failReason, ZonedDateTime.now(), ZonedDateTime.now()
            );
            given(paymentRepository.save(any(Payment.class))).willReturn(failedPayment);

            PgPaymentResponse callbackData = new PgPaymentResponse(
                    transactionId, String.valueOf(orderId), PgPaymentStatus.REJECTED,
                    50000L, "SAMSUNG", "1234",
                    ZonedDateTime.now(), ZonedDateTime.now(), failReason
            );

            // Act
            PaymentResult result = paymentService.handleCallback(callbackData);

            // Assert
            assertThat(result.status()).isEqualTo(PaymentStatus.FAIL);
            assertThat(result.failReason()).isEqualTo(failReason);
        }

        @Test
        @DisplayName("실패 - 결제 정보를 찾을 수 없음")
        void 결제_정보_없음_실패() {
            // Arrange
            String transactionId = "TXN-UNKNOWN";

            given(paymentRepository.findByTransactionId(transactionId)).willReturn(Optional.empty());

            PgPaymentResponse callbackData = new PgPaymentResponse(
                    transactionId, "100", PgPaymentStatus.APPROVED,
                    50000L, "SAMSUNG", "1234",
                    ZonedDateTime.now(), ZonedDateTime.now(), null
            );

            // Act & Assert
            assertThatThrownBy(() -> paymentService.handleCallback(callbackData))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> {
                        CoreException ce = (CoreException) ex;
                        assertThat(ce.getErrorType()).isEqualTo(ErrorType.PAYMENT_NOT_FOUND);
                    });
        }
    }

    private Order createTestOrder(Long orderId, Long userId, Long amount) {
        OrderItem item = OrderItem.create(1L, "테스트 상품", 1, new Money(amount));
        return Order.reconstitute(orderId, userId, List.of(item), new Money(amount),
                com.loopers.domain.order.OrderStatus.CREATED, ZonedDateTime.now());
    }
}

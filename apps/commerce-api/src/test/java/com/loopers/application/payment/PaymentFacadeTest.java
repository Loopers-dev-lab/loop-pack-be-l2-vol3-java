package com.loopers.application.payment;

import com.loopers.application.order.OrderFacade;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.payment.GatewayPaymentResult;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentCompensationService;
import com.loopers.domain.payment.PaymentModel;
import com.loopers.domain.payment.PaymentService;
import com.loopers.domain.product.StockService;
import com.loopers.support.enums.CardType;
import com.loopers.support.enums.OrderStatus;
import com.loopers.support.enums.OrderType;
import com.loopers.support.enums.PaymentStatus;
import com.loopers.support.enums.RestoreReason;
import com.loopers.support.enums.RestoreTriggerSource;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentFacade 단위 테스트")
class PaymentFacadeTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final Long PAYMENT_ID = 200L;
    private static final CardType CARD_TYPE = CardType.SAMSUNG;
    private static final String CARD_NO = "1234-5678-9012-3456";
    private static final String TRANSACTION_KEY = "txn-abc-123";
    private static final BigDecimal AMOUNT = BigDecimal.valueOf(30000);

    @Mock OrderService orderService;
    @Mock OrderFacade orderFacade;
    @Mock PaymentService paymentService;
    @Mock PaymentCompensationService compensationService;
    @Mock StockService stockService;
    @Mock PaymentGateway paymentGateway;

    @InjectMocks
    PaymentFacade paymentFacade;

    private OrderModel createPendingOrder() {
        return OrderModel.create(USER_ID, OrderType.DIRECT, AMOUNT);
    }

    private PaymentModel createRequestedPayment() {
        return PaymentModel.create(ORDER_ID, USER_ID, CARD_TYPE, CARD_NO, AMOUNT);
    }

    private GatewayPaymentResult createSuccessPgResult() {
        return new GatewayPaymentResult(TRANSACTION_KEY, true, "PENDING", null);
    }

    @Nested
    @DisplayName("requestPayment")
    class RequestPayment {

        @Test
        @DisplayName("정상 결제 요청 성공")
        void requestPayment_WithValidInput_ShouldSuccess() {
            OrderModel order = createPendingOrder();
            PaymentModel payment = createRequestedPayment();
            GatewayPaymentResult pgResult = createSuccessPgResult();

            when(orderService.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(order);
            when(paymentService.hasActivePayment(any())).thenReturn(false);
            when(paymentService.createPayment(any(), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any()))
                    .thenReturn(payment);
            when(paymentGateway.requestPayment(any(), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any(), any()))
                    .thenReturn(pgResult);
            when(paymentService.assignTransactionKey(any(), eq(TRANSACTION_KEY)))
                    .thenReturn(payment);

            PaymentInfo result = paymentFacade.requestPayment(USER_ID, ORDER_ID, CARD_TYPE, CARD_NO);

            assertThat(result).isNotNull();
            assertThat(result.transactionKey()).isEqualTo(TRANSACTION_KEY);
            assertThat(result.status()).isEqualTo("PENDING");

            // TX 분리 검증: createPayment(TX-1) → requestPayment(NO TX) → assignTransactionKey(TX-2)
            var inOrder = inOrder(paymentService, paymentGateway);
            inOrder.verify(paymentService).createPayment(any(), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any());
            inOrder.verify(paymentGateway).requestPayment(any(), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any(), any());
            inOrder.verify(paymentService).assignTransactionKey(any(), eq(TRANSACTION_KEY));
        }

        @Test
        @DisplayName("만료된 주문에 대한 결제 요청 시 PAYMENT_NOT_PAYABLE")
        void requestPayment_WithExpiredOrder_ShouldThrowPaymentNotPayable() {
            OrderModel order = mock(OrderModel.class);
            when(order.getStatus()).thenReturn(OrderStatus.PENDING_PAYMENT);
            when(order.isTimeExpired()).thenReturn(true);
            when(orderService.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(order);

            assertThatThrownBy(() -> paymentFacade.requestPayment(USER_ID, ORDER_ID, CARD_TYPE, CARD_NO))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_NOT_PAYABLE));

            verify(paymentService, never()).createPayment(any(), any(), any(), any(), any());
            verify(paymentGateway, never()).requestPayment(any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("이미 결제 진행 중인 주문 시 PAYMENT_ALREADY_IN_PROGRESS")
        void requestPayment_WithActivePayment_ShouldThrowAlreadyInProgress() {
            OrderModel order = createPendingOrder();
            when(orderService.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(order);
            when(paymentService.hasActivePayment(any())).thenReturn(true);

            assertThatThrownBy(() -> paymentFacade.requestPayment(USER_ID, ORDER_ID, CARD_TYPE, CARD_NO))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_ALREADY_IN_PROGRESS));

            verify(paymentService, never()).createPayment(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("PG 호출 중 일반 예외 발생 시 즉시 FAILED 전환 후 PAYMENT_PG_ERROR")
        void requestPayment_WithUnexpectedException_ShouldMarkPaymentFailedAndThrow() {
            OrderModel order = createPendingOrder();
            PaymentModel payment = createRequestedPayment();

            when(orderService.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(order);
            when(paymentService.hasActivePayment(any())).thenReturn(false);
            when(paymentService.createPayment(any(), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any()))
                    .thenReturn(payment);
            when(paymentGateway.requestPayment(any(), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any(), any()))
                    .thenThrow(new RuntimeException("PG 연결 실패"));

            assertThatThrownBy(() -> paymentFacade.requestPayment(USER_ID, ORDER_ID, CARD_TYPE, CARD_NO))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_PG_ERROR));

            // Phase A: 예상치 못한 예외 → 즉시 FAILED 전환
            verify(paymentService).completePayment(any(), eq(PaymentStatus.FAILED), anyString());
            verify(paymentService, never()).assignTransactionKey(any(), any());
        }

        @Test
        @DisplayName("PG 에러(502) 시 즉시 FAILED 전환")
        void requestPayment_WithPgErrorCoreException_ShouldMarkPaymentFailed() {
            OrderModel order = createPendingOrder();
            PaymentModel payment = createRequestedPayment();

            when(orderService.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(order);
            when(paymentService.hasActivePayment(any())).thenReturn(false);
            when(paymentService.createPayment(any(), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any()))
                    .thenReturn(payment);
            when(paymentGateway.requestPayment(any(), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any(), any()))
                    .thenThrow(new CoreException(ErrorType.PAYMENT_PG_ERROR, "PG 500 에러"));

            assertThatThrownBy(() -> paymentFacade.requestPayment(USER_ID, ORDER_ID, CARD_TYPE, CARD_NO))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_PG_ERROR));

            // Phase A: PG 에러(확실한 실패) → 즉시 FAILED
            verify(paymentService).completePayment(any(), eq(PaymentStatus.FAILED), anyString());
        }

        @Test
        @DisplayName("CB OPEN(503) 시 즉시 FAILED 전환")
        void requestPayment_WithCbOpen_ShouldMarkPaymentFailed() {
            OrderModel order = createPendingOrder();
            PaymentModel payment = createRequestedPayment();

            when(orderService.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(order);
            when(paymentService.hasActivePayment(any())).thenReturn(false);
            when(paymentService.createPayment(any(), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any()))
                    .thenReturn(payment);
            when(paymentGateway.requestPayment(any(), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any(), any()))
                    .thenThrow(new CoreException(ErrorType.PAYMENT_SERVICE_UNAVAILABLE));

            assertThatThrownBy(() -> paymentFacade.requestPayment(USER_ID, ORDER_ID, CARD_TYPE, CARD_NO))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_SERVICE_UNAVAILABLE));

            // Phase A: CB OPEN(확실한 실패, PG 미도달) → 즉시 FAILED
            verify(paymentService).completePayment(any(), eq(PaymentStatus.FAILED), anyString());
        }

        @Test
        @DisplayName("PG 타임아웃(504) 시 REQUESTED 유지 — FAILED 전환하지 않음")
        void requestPayment_WithTimeout_ShouldKeepRequested() {
            OrderModel order = createPendingOrder();
            PaymentModel payment = createRequestedPayment();

            when(orderService.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(order);
            when(paymentService.hasActivePayment(any())).thenReturn(false);
            when(paymentService.createPayment(any(), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any()))
                    .thenReturn(payment);
            when(paymentGateway.requestPayment(any(), eq(USER_ID), eq(CARD_TYPE), eq(CARD_NO), any(), any()))
                    .thenThrow(new CoreException(ErrorType.PAYMENT_PG_TIMEOUT));

            assertThatThrownBy(() -> paymentFacade.requestPayment(USER_ID, ORDER_ID, CARD_TYPE, CARD_NO))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_PG_TIMEOUT));

            // Phase A: 타임아웃(돈 빠졌을 수 있음) → REQUESTED 유지, FAILED 전환 안 함
            verify(paymentService, never()).completePayment(any(), eq(PaymentStatus.FAILED), anyString());
        }

        @Test
        @DisplayName("취소된 주문에 대한 결제 요청 시 PAYMENT_NOT_PAYABLE")
        void requestPayment_WithCancelledOrder_ShouldThrowPaymentNotPayable() {
            OrderModel order = mock(OrderModel.class);
            when(order.getStatus()).thenReturn(OrderStatus.CANCELLED);
            when(orderService.findByIdAndUserId(ORDER_ID, USER_ID)).thenReturn(order);

            assertThatThrownBy(() -> paymentFacade.requestPayment(USER_ID, ORDER_ID, CARD_TYPE, CARD_NO))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_NOT_PAYABLE));
        }
    }

    @Nested
    @DisplayName("handleCallback")
    class HandleCallback {

        @Test
        @DisplayName("SUCCESS 콜백 시 결제 완료 및 재고 확정")
        void handleCallback_WithSuccess_ShouldCompletePaymentAndCommitStock() {
            PaymentModel payment = createRequestedPayment();
            payment.assignTransactionKey(TRANSACTION_KEY);

            OrderItemModel item1 = OrderItemModel.create(ORDER_ID, 1, USER_ID, 10L, 2,
                    "상품A", BigDecimal.valueOf(10000), "brand-1", "브랜드A", null);
            OrderItemModel item2 = OrderItemModel.create(ORDER_ID, 2, USER_ID, 5L, 1,
                    "상품B", BigDecimal.valueOf(10000), "brand-2", "브랜드B", null);

            when(paymentService.findByTransactionKey(TRANSACTION_KEY)).thenReturn(Optional.of(payment));
            when(paymentService.completePayment(any(), eq(PaymentStatus.SUCCESS), isNull())).thenReturn(true);
            when(orderService.findOrderItems(ORDER_ID)).thenReturn(List.of(item1, item2));
            when(orderService.markAsPaid(ORDER_ID)).thenReturn(true);

            paymentFacade.handleCallback(TRANSACTION_KEY, "SUCCESS", null);

            verify(paymentService).completePayment(any(), eq(PaymentStatus.SUCCESS), isNull());
            verify(orderService).markAsPaid(ORDER_ID);
            // productId 오름차순 확인: 5L → 10L
            var inOrder = inOrder(stockService);
            inOrder.verify(stockService).commit(5L, 1);
            inOrder.verify(stockService).commit(10L, 2);
        }

        @Test
        @DisplayName("FAILED 콜백 + 다른 REQUESTED 존재 시 재고 release 안 함")
        void handleCallback_WithFailed_AndOtherActive_ShouldNotExpireOrder() {
            PaymentModel payment = createRequestedPayment();
            payment.assignTransactionKey(TRANSACTION_KEY);

            when(paymentService.findByTransactionKey(TRANSACTION_KEY)).thenReturn(Optional.of(payment));
            when(paymentService.completePayment(any(), eq(PaymentStatus.FAILED), eq("잔액 부족"))).thenReturn(true);
            when(paymentService.hasActivePayment(ORDER_ID)).thenReturn(true);

            paymentFacade.handleCallback(TRANSACTION_KEY, "FAILED", "잔액 부족");

            verify(paymentService).completePayment(any(), eq(PaymentStatus.FAILED), eq("잔액 부족"));
            verify(stockService, never()).commit(any(), anyInt());
            verify(orderFacade, never()).expireOrder(any(), any(), any());
        }

        @Test
        @DisplayName("모든 결제 FAILED 시 주문을 즉시 만료시킨다")
        void handleCallback_WithAllPaymentsFailed_ShouldExpireOrder() {
            PaymentModel payment = createRequestedPayment();
            payment.assignTransactionKey(TRANSACTION_KEY);

            when(paymentService.findByTransactionKey(TRANSACTION_KEY)).thenReturn(Optional.of(payment));
            when(paymentService.completePayment(any(), eq(PaymentStatus.FAILED), eq("잔액 부족"))).thenReturn(true);
            when(paymentService.hasActivePayment(ORDER_ID)).thenReturn(false);

            paymentFacade.handleCallback(TRANSACTION_KEY, "FAILED", "잔액 부족");

            verify(orderFacade).expireOrder(ORDER_ID, RestoreReason.PAYMENT_FAILED, RestoreTriggerSource.PG_WEBHOOK);
            verify(stockService, never()).commit(any(), anyInt());
        }

        @Test
        @DisplayName("중복 콜백 시 멱등 처리 (무시)")
        void handleCallback_WithDuplicateCallback_ShouldBeIdempotent() {
            PaymentModel payment = createRequestedPayment();
            payment.assignTransactionKey(TRANSACTION_KEY);

            when(paymentService.findByTransactionKey(TRANSACTION_KEY)).thenReturn(Optional.of(payment));
            when(paymentService.completePayment(any(), eq(PaymentStatus.SUCCESS), isNull())).thenReturn(false);

            paymentFacade.handleCallback(TRANSACTION_KEY, "SUCCESS", null);

            // CAS 실패(이미 처리) → 재고 확정 호출 없음
            verify(stockService, never()).commit(any(), anyInt());
        }

        @Test
        @DisplayName("잘못된 transactionKey 시 PAYMENT_NOT_FOUND")
        void handleCallback_WithInvalidTransactionKey_ShouldThrowPaymentNotFound() {
            when(paymentService.findByTransactionKey("invalid-key")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> paymentFacade.handleCallback("invalid-key", "SUCCESS", null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_NOT_FOUND));
        }

        @Test
        @DisplayName("SUCCESS 콜백 + stock commit 실패 시 보정 테이블에 기록하고 예외를 재throw한다")
        void handleCallback_WithStockCommitFailure_ShouldRecordCompensationAndRethrow() {
            PaymentModel payment = createRequestedPayment();
            payment.assignTransactionKey(TRANSACTION_KEY);

            OrderItemModel item = OrderItemModel.create(ORDER_ID, 1, USER_ID, 10L, 2,
                    "상품A", BigDecimal.valueOf(10000), "brand-1", "브랜드A", null);

            when(paymentService.findByTransactionKey(TRANSACTION_KEY)).thenReturn(Optional.of(payment));
            when(paymentService.completePayment(any(), eq(PaymentStatus.SUCCESS), isNull())).thenReturn(true);
            when(orderService.findOrderItems(ORDER_ID)).thenReturn(List.of(item));
            doThrow(new RuntimeException("stock 레코드 없음"))
                    .when(stockService).commit(10L, 2);

            assertThatThrownBy(() -> paymentFacade.handleCallback(TRANSACTION_KEY, "SUCCESS", null))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessage("stock 레코드 없음");

            // 보정 테이블에 기록됨
            verify(compensationService).recordFailedCommit(any(), eq(ORDER_ID), eq("stock 레코드 없음"));
            // markAsPaid는 호출되지 않음 (commit 실패로 중단)
            verify(orderService, never()).markAsPaid(any());
        }

        @Test
        @DisplayName("SUCCESS 콜백 정상 처리 시 보정 테이블에 기록하지 않는다")
        void handleCallback_WithSuccessfulCommit_ShouldNotRecordCompensation() {
            PaymentModel payment = createRequestedPayment();
            payment.assignTransactionKey(TRANSACTION_KEY);

            OrderItemModel item = OrderItemModel.create(ORDER_ID, 1, USER_ID, 10L, 2,
                    "상품A", BigDecimal.valueOf(10000), "brand-1", "브랜드A", null);

            when(paymentService.findByTransactionKey(TRANSACTION_KEY)).thenReturn(Optional.of(payment));
            when(paymentService.completePayment(any(), eq(PaymentStatus.SUCCESS), isNull())).thenReturn(true);
            when(orderService.findOrderItems(ORDER_ID)).thenReturn(List.of(item));
            when(orderService.markAsPaid(ORDER_ID)).thenReturn(true);

            paymentFacade.handleCallback(TRANSACTION_KEY, "SUCCESS", null);

            verify(compensationService, never()).recordFailedCommit(any(), any(), any());
        }
    }
}

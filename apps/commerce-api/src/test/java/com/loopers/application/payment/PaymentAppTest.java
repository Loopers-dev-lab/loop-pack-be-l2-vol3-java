package com.loopers.application.payment;

import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderRepository;
import com.loopers.domain.payment.PaymentRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentAppTest {

    OrderRepository orderRepository = mock(OrderRepository.class);
    PaymentRepository paymentRepository = mock(PaymentRepository.class);

    PaymentApp paymentApp = new PaymentApp(orderRepository, paymentRepository);

    @DisplayName("결제 요청 시, ")
    @Nested
    class Pay {

        @DisplayName("유효한 주문이고 본인 주문이면, Payment 가 저장되고 PaymentInfo 를 반환한다.")
        @Test
        void returnsPaymentInfo_andSavesPayment_whenValidOrder() {
            // arrange
            String orderId = "20260318-ABCD12";
            Long userId = 1L;
            String cardType = "신한카드";
            String cardNo = "1234-5678-9012-3456";

            Order order = mock(Order.class);
            when(order.userId()).thenReturn(userId);
            when(order.paymentAmount()).thenReturn(50000L);
            when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

            // act
            PaymentInfo result = paymentApp.pay(orderId, cardType, cardNo, userId);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(orderId);
            verify(paymentRepository).save(any());
        }

        @DisplayName("존재하지 않는 주문번호이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenOrderNotFound() {
            // arrange
            String orderId = "20260318-NOTFOUND";
            when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentApp.pay(orderId, "신한카드", "1234-5678-9012-3456", 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(result.getCustomMessage()).isEqualTo("찾을 수 없는 주문번호입니다.");
        }

        @DisplayName("이미 SUCCESS 상태인 Payment 가 존재하면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenPaymentAlreadySucceeded() {
            // arrange
            String orderId = "20260318-ABCD12";
            Long userId = 1L;

            Order order = mock(Order.class);
            when(order.userId()).thenReturn(userId);
            when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

            Payment existingPayment = mock(Payment.class);
            when(existingPayment.status()).thenReturn(PaymentStatus.SUCCESS);
            when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existingPayment));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentApp.pay(orderId, "신한카드", "1234-5678-9012-3456", userId)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(result.getCustomMessage()).isEqualTo("이미 결제된 주문입니다.");
            verify(paymentRepository, never()).save(any());
        }

        @DisplayName("이미 PENDING 상태인 Payment 가 존재하면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenPaymentIsPending() {
            // arrange
            String orderId = "20260318-ABCD12";
            Long userId = 1L;

            Order order = mock(Order.class);
            when(order.userId()).thenReturn(userId);
            when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

            Payment existingPayment = mock(Payment.class);
            when(existingPayment.status()).thenReturn(PaymentStatus.PENDING);
            when(existingPayment.orderId()).thenReturn(orderId);
            when(existingPayment.cardType()).thenReturn("신한카드");
            when(existingPayment.cardNo()).thenReturn("1234-5678-9012-3456");
            when(existingPayment.amount()).thenReturn(50000L);
            when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existingPayment));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                    paymentApp.pay(orderId, "신한카드", "1234-5678-9012-3456", userId)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
            assertThat(result.getCustomMessage()).isEqualTo("이미 진행 중인 결제입니다.");
            verify(paymentRepository, never()).save(any());
        }

        @DisplayName("이미 FAILED 상태인 Payment 가 존재하면, reset 후 PaymentInfo 를 반환한다.")
        @Test
        void resetsAndReturnsPaymentInfo_whenPaymentIsFailed() {
            // arrange
            String orderId = "20260318-ABCD12";
            Long userId = 1L;
            String newCardType = "국민카드";
            String newCardNo = "9999-8888-7777-6666";

            Order order = mock(Order.class);
            when(order.userId()).thenReturn(userId);
            when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

            Payment existingPayment = mock(Payment.class);
            when(existingPayment.status()).thenReturn(PaymentStatus.FAILED);
            when(existingPayment.orderId()).thenReturn(orderId);
            when(existingPayment.cardType()).thenReturn(newCardType);
            when(existingPayment.cardNo()).thenReturn(newCardNo);
            when(existingPayment.amount()).thenReturn(50000L);
            when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(existingPayment));

            // act
            PaymentInfo result = paymentApp.pay(orderId, newCardType, newCardNo, userId);

            // assert
            assertThat(result.orderId()).isEqualTo(orderId);
            verify(existingPayment).reset(newCardType, newCardNo);
            verify(paymentRepository, never()).save(any());
        }

        @DisplayName("본인 주문이 아니면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenNotOrderOwner() {
            // arrange
            String orderId = "20260318-ABCD12";
            Long userId = 1L;
            Long anotherUserId = 2L;

            Order order = mock(Order.class);
            when(order.userId()).thenReturn(anotherUserId);
            when(orderRepository.findByOrderId(orderId)).thenReturn(Optional.of(order));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentApp.pay(orderId, "신한카드", "1234-5678-9012-3456", userId)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
            assertThat(result.getCustomMessage()).isEqualTo("본인의 주문에 대해서만 결제 가능합니다.");
        }
    }

    @DisplayName("결제 조회 시, ")
    @Nested
    class GetPayment {

        @DisplayName("본인의 orderId 이면, PaymentInfo 를 반환한다.")
        @Test
        void returnsPaymentInfo_whenOwnerRequests() {
            // arrange
            String orderId = "20260318-ABCD12";
            Long userId = 1L;

            Payment payment = mock(Payment.class);
            when(payment.userId()).thenReturn(userId);
            when(payment.orderId()).thenReturn(orderId);
            when(payment.status()).thenReturn(PaymentStatus.SUCCESS);
            when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

            // act
            PaymentInfo result = paymentApp.getPayment(orderId, userId);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.orderId()).isEqualTo(orderId);
        }

        @DisplayName("존재하지 않는 orderId 이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenPaymentNotFound() {
            // arrange
            String orderId = "20260318-NOTFOUND";
            when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentApp.getPayment(orderId, 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("다른 사용자의 orderId 이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenNotOwner() {
            // arrange
            String orderId = "20260318-ABCD12";
            Long userId = 1L;
            Long anotherUserId = 2L;

            Payment payment = mock(Payment.class);
            when(payment.userId()).thenReturn(anotherUserId);
            when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentApp.getPayment(orderId, userId)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("PG 응답 반영 시, ")
    @Nested
    class ApplyPgResponse {

        @DisplayName("존재하는 orderId 이면, applyPgResult 가 반영된다.")
        @Test
        void appliesPgResult_whenPaymentExists() {
            // arrange
            String orderId = "20260318-ABCD12";
            com.loopers.domain.payment.Payment payment = mock(com.loopers.domain.payment.Payment.class);
            when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.of(payment));

            // act
            paymentApp.applyPgResponse(orderId, "tx-key-001", "SUCCESS", null);

            // assert
            verify(payment).applyPgResult("tx-key-001", com.loopers.domain.payment.PaymentStatus.SUCCESS, null);
        }

        @DisplayName("존재하지 않는 orderId 이면, CoreException 이 발생한다.")
        @Test
        void throwsCoreException_whenPaymentNotFound() {
            // arrange
            String orderId = "20260318-NOTFOUND";
            when(paymentRepository.findByOrderId(orderId)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentApp.applyPgResponse(orderId, "tx-key-001", "SUCCESS", null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            assertThat(result.getCustomMessage()).isEqualTo("찾을 수 없는 결제번호입니다.");
        }
    }
}

package com.loopers.application.payment;

import com.loopers.application.payment.pg.PgClient;
import com.loopers.application.payment.pg.PgPaymentDto;
import com.loopers.application.payment.pg.exception.PgBadRequestException;
import com.loopers.application.payment.pg.exception.PgCircuitOpenException;
import com.loopers.application.payment.pg.exception.PgConnectTimeoutException;
import com.loopers.application.payment.pg.exception.PgReadTimeoutException;
import com.loopers.application.payment.pg.exception.PgServerException;
import com.loopers.domain.payment.PaymentStatus;
import java.time.ZonedDateTime;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentFacadeTest {

    PaymentApp paymentApp = mock(PaymentApp.class);
    PgClient pgClient = mock(PgClient.class);

    PaymentFacade paymentFacade = new PaymentFacade(paymentApp, pgClient);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentFacade, "callbackUrl", "http://localhost:8080/callback");
    }

    @DisplayName("결제 처리 시, ")
    @Nested
    class ProcessPayment {

        @DisplayName("PG 요청이 성공하면, PG 응답이 반영된다.")
        @Test
        void appliesPgResponse_whenPgRequestSucceeds() {
            // arrange
            String orderId = "20260318-ABCD12";
            PaymentInfo paymentInfo = new PaymentInfo(orderId, "신한카드", "1234-5678-9012-3456", 50000L, PaymentStatus.PENDING, null, null, ZonedDateTime.now());
            PgPaymentDto.TransactionResponse pgResponse = new PgPaymentDto.TransactionResponse(
                    "tx-key-001", PgPaymentDto.TransactionStatus.SUCCESS, null);

            when(paymentApp.pay(orderId, "신한카드", "1234-5678-9012-3456", 1L)).thenReturn(paymentInfo);
            when(pgClient.requestPayment(any())).thenReturn(pgResponse);

            // act
            paymentFacade.processPayment(orderId, "신한카드", "1234-5678-9012-3456", 1L);

            // assert
            verify(paymentApp).applyPgResponse(orderId, "tx-key-001", "SUCCESS", null);
        }

        @DisplayName("PG 요청에서 400 에러가 발생하면, Payment 를 FAILED 처리하고 예외를 재발생시킨다.")
        @Test
        void failsPaymentAndRethrows_whenPgReturns400() {
            // arrange
            String orderId = "20260318-ABCD12";
            PaymentInfo paymentInfo = new PaymentInfo(orderId, "신한카드", "1234-5678-9012-3456", 50000L, PaymentStatus.PENDING, null, null, ZonedDateTime.now());

            when(paymentApp.pay(orderId, "신한카드", "1234-5678-9012-3456", 1L)).thenReturn(paymentInfo);
            when(pgClient.requestPayment(any())).thenThrow(new PgBadRequestException("PG 결제 요청이 잘못되었습니다."));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentFacade.processPayment(orderId, "신한카드", "1234-5678-9012-3456", 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INTERNAL_ERROR);
            verify(paymentApp).applyPgResponse(eq(orderId), isNull(), eq("FAILED"), any());
        }

        @DisplayName("PG 서버 오류(5xx) 로 Retry 소진 시, Payment 를 FAILED 처리하고 예외를 재발생시킨다.")
        @Test
        void failsPaymentAndRethrows_whenPgServerError() {
            // arrange
            String orderId = "20260318-ABCD12";
            PaymentInfo paymentInfo = new PaymentInfo(orderId, "신한카드", "1234-5678-9012-3456", 50000L, PaymentStatus.PENDING, null, null, ZonedDateTime.now());

            when(paymentApp.pay(orderId, "신한카드", "1234-5678-9012-3456", 1L)).thenReturn(paymentInfo);
            when(pgClient.requestPayment(any())).thenThrow(new PgServerException("PG 서버 오류가 발생했습니다."));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentFacade.processPayment(orderId, "신한카드", "1234-5678-9012-3456", 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_GATEWAY);
            verify(paymentApp).applyPgResponse(eq(orderId), isNull(), eq("FAILED"), any());
        }

        @DisplayName("PG 연결 타임아웃으로 Retry 소진 시, Payment 를 FAILED 처리하고 예외를 재발생시킨다.")
        @Test
        void failsPaymentAndRethrows_whenPgConnectTimeout() {
            // arrange
            String orderId = "20260318-ABCD12";
            PaymentInfo paymentInfo = new PaymentInfo(orderId, "신한카드", "1234-5678-9012-3456", 50000L, PaymentStatus.PENDING, null, null, ZonedDateTime.now());

            when(paymentApp.pay(orderId, "신한카드", "1234-5678-9012-3456", 1L)).thenReturn(paymentInfo);
            when(pgClient.requestPayment(any())).thenThrow(new PgConnectTimeoutException("PG 서버에 연결할 수 없습니다."));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentFacade.processPayment(orderId, "신한카드", "1234-5678-9012-3456", 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_GATEWAY);
            verify(paymentApp).applyPgResponse(eq(orderId), isNull(), eq("FAILED"), any());
        }

        @DisplayName("PG 읽기 타임아웃 발생 시, Payment 를 PENDING 유지하고 정상 응답한다.")
        @Test
        void keepsPending_whenPgReadTimeout() {
            // arrange
            String orderId = "20260318-ABCD12";
            PaymentInfo paymentInfo = new PaymentInfo(orderId, "신한카드", "1234-5678-9012-3456", 50000L, PaymentStatus.PENDING, null, null, ZonedDateTime.now());

            when(paymentApp.pay(orderId, "신한카드", "1234-5678-9012-3456", 1L)).thenReturn(paymentInfo);
            when(pgClient.requestPayment(any())).thenThrow(new PgReadTimeoutException("PG 서버 응답 시간이 초과되었습니다."));

            // act
            paymentFacade.processPayment(orderId, "신한카드", "1234-5678-9012-3456", 1L);

            // assert
            verify(paymentApp, never()).applyPgResponse(eq(orderId), isNull(), eq("FAILED"), any());
        }

        @DisplayName("Circuit Breaker Open 상태에서, Payment 를 FAILED 처리하고 예외를 재발생시킨다.")
        @Test
        void failsPaymentAndRethrows_whenCircuitBreakerOpen() {
            // arrange
            String orderId = "20260318-ABCD12";
            PaymentInfo paymentInfo = new PaymentInfo(orderId, "신한카드", "1234-5678-9012-3456", 50000L, PaymentStatus.PENDING, null, null, ZonedDateTime.now());

            when(paymentApp.pay(orderId, "신한카드", "1234-5678-9012-3456", 1L)).thenReturn(paymentInfo);
            when(pgClient.requestPayment(any())).thenThrow(new PgCircuitOpenException("PG 서버에 연결할 수 없습니다."));

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                paymentFacade.processPayment(orderId, "신한카드", "1234-5678-9012-3456", 1L)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_GATEWAY);
            verify(paymentApp).applyPgResponse(eq(orderId), isNull(), eq("FAILED"), any());
        }
    }

}

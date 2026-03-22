package com.loopers.infrastructure.payment.pg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.AopTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRequest;
import com.loopers.support.MockPgClientConfig;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;

@SpringBootTest
@Import(MockPgClientConfig.class)
class PaymentGatewayRetryIntegrationTest {

    private PgPaymentHttpInterface pgClient;

    @Autowired
    private PgPaymentHttpInterface pgClientProxy;

    @Autowired
    private PaymentGateway paymentGateway;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private RetryRegistry retryRegistry;

    @BeforeEach
    void setUp() {
        pgClient = AopTestUtils.getTargetObject(pgClientProxy);
        reset(pgClient);
        circuitBreakerRegistry.circuitBreaker("pg-payment").reset();
        circuitBreakerRegistry.circuitBreaker("pg-query").reset();
    }

    @DisplayName("PG 조회 Retry가 동작할 때,")
    @Nested
    class QueryRetry {

        @DisplayName("일시적 오류 발생 시 재시도 후 성공한다.")
        @Test
        void retriesAndSucceedsOnTransientError() {
            // arrange
            PgTransactionDetailResponse detailResponse = new PgTransactionDetailResponse(
                    "tx-key-001", "order-001", "SAMSUNG", "1234-5678-9012-3456",
                    10000L, "SUCCESS", "정상 승인되었습니다.");
            PgApiResponse<PgTransactionDetailResponse> successResponse = new PgApiResponse<>(
                    new PgApiResponse.Metadata("SUCCESS", null, null), detailResponse);

            given(pgClient.getTransaction(anyLong(), anyString()))
                    .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "PG 서버 오류"))
                    .willReturn(successResponse);

            // act
            var result = paymentGateway.getTransaction(1L, "tx-key-001");

            // assert
            assertThat(result.transactionKey()).isEqualTo("tx-key-001");
            verify(pgClient, times(2)).getTransaction(anyLong(), anyString());
        }

        @DisplayName("최대 재시도 초과 시 예외가 전파된다.")
        @Test
        void throwsExceptionWhenMaxRetriesExceeded() {
            // arrange
            given(pgClient.getTransaction(anyLong(), anyString()))
                    .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "PG 서버 오류"));

            // act & assert
            assertThatThrownBy(() -> paymentGateway.getTransaction(1L, "tx-key-001"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.PAYMENT_GATEWAY_UNAVAILABLE));

            verify(pgClient, times(3)).getTransaction(anyLong(), anyString());
        }

        @DisplayName("4xx 오류는 재시도하지 않는다.")
        @Test
        void doesNotRetryOn4xxError() {
            // arrange
            given(pgClient.getTransaction(anyLong(), anyString()))
                    .willThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST, "잘못된 요청"));

            // act & assert
            assertThatThrownBy(() -> paymentGateway.getTransaction(1L, "tx-key-001"))
                    .isInstanceOf(CoreException.class);

            verify(pgClient, times(1)).getTransaction(anyLong(), anyString());
        }
    }

    @DisplayName("PG 쓰기 Retry가 동작할 때,")
    @Nested
    class PaymentRetry {

        @DisplayName("requestPayment는 재시도하지 않는다.")
        @Test
        void doesNotRetryRequestPayment() {
            // arrange
            given(pgClient.requestPayment(anyLong(), any(PgPaymentRequest.class)))
                    .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR, "PG 서버 오류"));

            PaymentRequest request = new PaymentRequest(
                    "order-001", "SAMSUNG", "1234-5678-9012-3456", 10000L,
                    "http://localhost:8080/api/v1/payments/callback");

            // act & assert
            assertThatThrownBy(() -> paymentGateway.requestPayment(1L, request))
                    .isInstanceOf(CoreException.class);

            verify(pgClient, times(1)).requestPayment(anyLong(), any(PgPaymentRequest.class));
        }
    }
}

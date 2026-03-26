package com.loopers.infrastructure.pg;

import com.loopers.domain.payment.PaymentGateway.PaymentGatewayDetailResponse;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayOrderResponse;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayRequest;
import com.loopers.domain.payment.PaymentGateway.PaymentGatewayResponse;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
@DisplayName("PgPaymentGateway 단위 테스트")
class PgPaymentGatewayTest {

    @Mock
    private PgPaymentClient pgPaymentClient;

    @InjectMocks
    private PgPaymentGateway pgPaymentGateway;

    @Nested
    @DisplayName("requestPayment - 결제 요청")
    class RequestPayment {

        @Test
        @DisplayName("성공: PG에 결제를 요청하고 transactionKey를 반환한다")
        void requestPayment_success() {
            // Given
            PaymentGatewayRequest request = new PaymentGatewayRequest(
                    "1", "SAMSUNG", "1234-5678", 50000L, "http://localhost:8080/api/v1/payments/callback"
            );
            PgPaymentResponse.TransactionResponse txnResponse = new PgPaymentResponse.TransactionResponse(
                    "txn-abc-123", "PENDING", null
            );
            PgPaymentResponse<PgPaymentResponse.TransactionResponse> response = new PgPaymentResponse<>(
                    new PgPaymentResponse.Metadata("SUCCESS", null, null), txnResponse
            );

            given(pgPaymentClient.requestPayment(eq("1"), any(PgPaymentRequest.class))).willReturn(response);

            // When
            PaymentGatewayResponse result = pgPaymentGateway.requestPayment("1", request);

            // Then
            assertThat(result.transactionKey()).isEqualTo("txn-abc-123");
            assertThat(result.status()).isEqualTo("PENDING");
            then(pgPaymentClient).should().requestPayment(eq("1"), any(PgPaymentRequest.class));
        }

        @Test
        @DisplayName("실패: PG 응답이 FAIL이면 INTERNAL_ERROR 예외를 던진다")
        void requestPayment_pgFail() {
            // Given
            PaymentGatewayRequest request = new PaymentGatewayRequest(
                    "1", "SAMSUNG", "1234-5678", 50000L, "http://localhost:8080/api/v1/payments/callback"
            );
            PgPaymentResponse<PgPaymentResponse.TransactionResponse> response = new PgPaymentResponse<>(
                    new PgPaymentResponse.Metadata("FAIL", "PG_ERROR", "결제 처리 실패"), null
            );

            given(pgPaymentClient.requestPayment(eq("1"), any(PgPaymentRequest.class))).willReturn(response);

            // When & Then
            assertThatThrownBy(() -> pgPaymentGateway.requestPayment("1", request))
                    .isInstanceOf(CoreException.class)
                    .hasFieldOrPropertyWithValue("errorType", ErrorType.INTERNAL_ERROR);
        }
    }

    @Nested
    @DisplayName("getTransaction - 거래 조회")
    class GetTransaction {

        @Test
        @DisplayName("성공: transactionKey로 거래 상세를 조회한다")
        void getTransaction_success() {
            // Given
            String transactionKey = "txn-abc-123";
            PgPaymentResponse.TransactionDetailResponse detail = new PgPaymentResponse.TransactionDetailResponse(
                    transactionKey, "1", "SAMSUNG", "1234", 50000L, "SUCCESS", null
            );
            PgPaymentResponse<PgPaymentResponse.TransactionDetailResponse> response = new PgPaymentResponse<>(
                    new PgPaymentResponse.Metadata("SUCCESS", null, null), detail
            );

            given(pgPaymentClient.getTransaction(eq("1"), eq(transactionKey))).willReturn(response);

            // When
            PaymentGatewayDetailResponse result = pgPaymentGateway.getTransaction("1", transactionKey);

            // Then
            assertThat(result.transactionKey()).isEqualTo(transactionKey);
            assertThat(result.status()).isEqualTo("SUCCESS");
        }
    }

    @Nested
    @DisplayName("getTransactionsByOrder - 주문별 거래 조회")
    class GetTransactionsByOrder {

        @Test
        @DisplayName("성공: orderId로 거래 목록을 조회한다")
        void getTransactionsByOrder_success() {
            // Given
            String orderId = "1";
            PgPaymentResponse.TransactionResponse txn = new PgPaymentResponse.TransactionResponse(
                    "txn-abc-123", "SUCCESS", null
            );
            PgPaymentResponse.OrderResponse orderResponse = new PgPaymentResponse.OrderResponse(orderId, List.of(txn));
            PgPaymentResponse<PgPaymentResponse.OrderResponse> response = new PgPaymentResponse<>(
                    new PgPaymentResponse.Metadata("SUCCESS", null, null), orderResponse
            );

            given(pgPaymentClient.getTransactionsByOrder(eq("1"), eq("ORD-000001"))).willReturn(response);

            // When
            PaymentGatewayOrderResponse result = pgPaymentGateway.getTransactionsByOrder("1", orderId);

            // Then
            assertThat(result.orderId()).isEqualTo(orderId);
            assertThat(result.transactions()).hasSize(1);
        }
    }
}

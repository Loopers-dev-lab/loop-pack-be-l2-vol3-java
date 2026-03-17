package com.loopers.infrastructure.payment.pg;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.loopers.domain.payment.OrderTransactionResult;
import com.loopers.domain.payment.PaymentRequest;
import com.loopers.domain.payment.TransactionDetailResult;
import com.loopers.domain.payment.TransactionResult;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentGatewayImplTest {

    @Mock
    private PgPaymentHttpInterface pgClient;

    @InjectMocks
    private PaymentGatewayImpl paymentGateway;

    @DisplayName("결제를 요청할 때,")
    @Nested
    class RequestPayment {

        @DisplayName("PG 응답을 도메인 결과로 변환한다.")
        @Test
        void convertsPgResponseToDomainResult() {
            // arrange
            PaymentRequest request = new PaymentRequest(
                    "order-001", "SAMSUNG", "1234-5678-9012-3456", 10000L,
                    "http://localhost:8080/api/v1/payments/callback");

            PgTransactionResponse pgResponse = new PgTransactionResponse(
                    "tx-key-001", "SUCCESS", "정상 승인되었습니다.");
            given(pgClient.requestPayment(eq(1L), any(PgPaymentRequest.class)))
                    .willReturn(new PgApiResponse<>(
                            new PgApiResponse.Metadata("SUCCESS", null, null), pgResponse));

            // act
            TransactionResult result = paymentGateway.requestPayment(1L, request);

            // assert
            assertAll(
                    () -> assertThat(result.transactionKey()).isEqualTo("tx-key-001"),
                    () -> assertThat(result.status()).isEqualTo("SUCCESS"),
                    () -> assertThat(result.reason()).isEqualTo("정상 승인되었습니다.")
            );
        }
    }

    @DisplayName("거래를 조회할 때,")
    @Nested
    class GetTransaction {

        @DisplayName("PG 상세 응답을 도메인 결과로 변환한다.")
        @Test
        void convertsPgDetailResponseToDomainResult() {
            // arrange
            PgTransactionDetailResponse pgResponse = new PgTransactionDetailResponse(
                    "tx-key-001", "order-001", "SAMSUNG", "1234-5678-9012-3456",
                    10000L, "SUCCESS", "정상 승인되었습니다.");
            given(pgClient.getTransaction(1L, "tx-key-001"))
                    .willReturn(new PgApiResponse<>(
                            new PgApiResponse.Metadata("SUCCESS", null, null), pgResponse));

            // act
            TransactionDetailResult result = paymentGateway.getTransaction(1L, "tx-key-001");

            // assert
            assertAll(
                    () -> assertThat(result.transactionKey()).isEqualTo("tx-key-001"),
                    () -> assertThat(result.orderId()).isEqualTo("order-001"),
                    () -> assertThat(result.cardType()).isEqualTo("SAMSUNG"),
                    () -> assertThat(result.cardNo()).isEqualTo("1234-5678-9012-3456"),
                    () -> assertThat(result.amount()).isEqualTo(10000L),
                    () -> assertThat(result.status()).isEqualTo("SUCCESS"),
                    () -> assertThat(result.reason()).isEqualTo("정상 승인되었습니다.")
            );
        }
    }

    @DisplayName("주문별 거래를 조회할 때,")
    @Nested
    class GetTransactionsByOrder {

        @DisplayName("PG 주문 응답을 도메인 결과로 변환한다.")
        @Test
        void convertsPgOrderResponseToDomainResult() {
            // arrange
            PgOrderResponse pgResponse = new PgOrderResponse("order-001", List.of(
                    new PgTransactionResponse("tx-key-001", "FAILED", "카드 한도 초과"),
                    new PgTransactionResponse("tx-key-002", "SUCCESS", "정상 승인되었습니다.")));
            given(pgClient.getTransactionsByOrder(1L, "order-001"))
                    .willReturn(new PgApiResponse<>(
                            new PgApiResponse.Metadata("SUCCESS", null, null), pgResponse));

            // act
            OrderTransactionResult result = paymentGateway.getTransactionsByOrder(1L, "order-001");

            // assert
            assertAll(
                    () -> assertThat(result.orderId()).isEqualTo("order-001"),
                    () -> assertThat(result.transactions()).hasSize(2),
                    () -> assertThat(result.transactions().get(0).transactionKey()).isEqualTo("tx-key-001"),
                    () -> assertThat(result.transactions().get(0).status()).isEqualTo("FAILED"),
                    () -> assertThat(result.transactions().get(1).transactionKey()).isEqualTo("tx-key-002"),
                    () -> assertThat(result.transactions().get(1).status()).isEqualTo("SUCCESS")
            );
        }
    }
}

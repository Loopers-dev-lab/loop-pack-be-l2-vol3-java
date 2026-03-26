package com.loopers.infrastructure.payment;

import com.loopers.domain.payment.GatewayPaymentResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PgPaymentResponse 테스트")
class PgPaymentResponseTest {

    @Test
    @DisplayName("toGatewayResult()로 PG 응답을 domain 타입으로 변환한다")
    void toGatewayResult_WithSuccessResponse_ShouldConvertToDomainType() {
        PgPaymentResponse pgResponse = new PgPaymentResponse("txn-abc", true, "SUCCESS", null);

        GatewayPaymentResult result = pgResponse.toGatewayResult();

        assertThat(result.transactionKey()).isEqualTo("txn-abc");
        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.reason()).isNull();
    }

    @Test
    @DisplayName("실패 응답도 정확히 변환된다")
    void toGatewayResult_WithFailedResponse_ShouldPreserveReason() {
        PgPaymentResponse pgResponse = new PgPaymentResponse("txn-def", false, "FAILED", "잔액 부족");

        GatewayPaymentResult result = pgResponse.toGatewayResult();

        assertThat(result.transactionKey()).isEqualTo("txn-def");
        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.reason()).isEqualTo("잔액 부족");
    }

    @Test
    @DisplayName("PENDING 응답도 정확히 변환된다")
    void toGatewayResult_WithPendingResponse_ShouldConvert() {
        PgPaymentResponse pgResponse = new PgPaymentResponse("txn-ghi", false, "PENDING", null);

        GatewayPaymentResult result = pgResponse.toGatewayResult();

        assertThat(result.isPending()).isTrue();
        assertThat(result.isCompleted()).isFalse();
    }
}

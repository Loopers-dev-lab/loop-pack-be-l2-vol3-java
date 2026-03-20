package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("GatewayPaymentResult 테스트")
class GatewayPaymentResultTest {

    @Test
    @DisplayName("status가 PENDING이면 isPending()은 true를 반환한다")
    void isPending_WithPendingStatus_ShouldReturnTrue() {
        GatewayPaymentResult result = new GatewayPaymentResult("txn-001", false, "PENDING", null);

        assertThat(result.isPending()).isTrue();
    }

    @Test
    @DisplayName("status가 SUCCESS이면 isPending()은 false를 반환한다")
    void isPending_WithSuccessStatus_ShouldReturnFalse() {
        GatewayPaymentResult result = new GatewayPaymentResult("txn-001", true, "SUCCESS", null);

        assertThat(result.isPending()).isFalse();
    }

    @Test
    @DisplayName("status가 SUCCESS이면 isCompleted()는 true를 반환한다")
    void isCompleted_WithSuccessStatus_ShouldReturnTrue() {
        GatewayPaymentResult result = new GatewayPaymentResult("txn-001", true, "SUCCESS", null);

        assertThat(result.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("status가 FAILED이면 isCompleted()는 true를 반환한다")
    void isCompleted_WithFailedStatus_ShouldReturnTrue() {
        GatewayPaymentResult result = new GatewayPaymentResult("txn-001", false, "FAILED", "잔액 부족");

        assertThat(result.isCompleted()).isTrue();
    }

    @Test
    @DisplayName("status가 PENDING이면 isCompleted()는 false를 반환한다")
    void isCompleted_WithPendingStatus_ShouldReturnFalse() {
        GatewayPaymentResult result = new GatewayPaymentResult("txn-001", false, "PENDING", null);

        assertThat(result.isCompleted()).isFalse();
    }
}

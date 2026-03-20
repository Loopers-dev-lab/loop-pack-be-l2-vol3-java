package com.loopers.domain.payment;

import com.loopers.support.enums.CompensationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentCompensationModel 단위 테스트")
class PaymentCompensationModelTest {

    @Test
    @DisplayName("생성 시 PENDING 상태, retryCount=0, maxRetries=3으로 초기화된다")
    void create_ShouldInitializeWithPendingStatus() {
        PaymentCompensationModel model = PaymentCompensationModel.create(1L, 100L, "stock 삭제됨");

        assertThat(model.getPaymentId()).isEqualTo(1L);
        assertThat(model.getOrderId()).isEqualTo(100L);
        assertThat(model.getFailureReason()).isEqualTo("stock 삭제됨");
        assertThat(model.getStatus()).isEqualTo(CompensationStatus.PENDING);
        assertThat(model.getRetryCount()).isZero();
        assertThat(model.getMaxRetries()).isEqualTo(3);
        assertThat(model.getCreatedAt()).isNotNull();
        assertThat(model.getResolvedAt()).isNull();
    }

    @Test
    @DisplayName("incrementRetry 호출 시 retryCount가 1 증가한다")
    void incrementRetry_ShouldIncreaseCount() {
        PaymentCompensationModel model = PaymentCompensationModel.create(1L, 100L, "에러");

        model.incrementRetry();

        assertThat(model.getRetryCount()).isEqualTo(1);
        assertThat(model.getStatus()).isEqualTo(CompensationStatus.PENDING);
    }

    @Test
    @DisplayName("maxRetries 도달 시 MANUAL_REQUIRED로 자동 전이된다")
    void incrementRetry_WhenMaxReached_ShouldTransitionToManualRequired() {
        PaymentCompensationModel model = PaymentCompensationModel.create(1L, 100L, "에러");

        model.incrementRetry(); // 1
        model.incrementRetry(); // 2
        model.incrementRetry(); // 3 → MANUAL_REQUIRED

        assertThat(model.getRetryCount()).isEqualTo(3);
        assertThat(model.getStatus()).isEqualTo(CompensationStatus.MANUAL_REQUIRED);
    }

    @Test
    @DisplayName("resolve 호출 시 RESOLVED 상태 + resolvedAt 설정")
    void resolve_ShouldSetResolvedStatus() {
        PaymentCompensationModel model = PaymentCompensationModel.create(1L, 100L, "에러");

        model.resolve();

        assertThat(model.getStatus()).isEqualTo(CompensationStatus.RESOLVED);
        assertThat(model.getResolvedAt()).isNotNull();
    }

    @Test
    @DisplayName("markAsManualRequired 호출 시 MANUAL_REQUIRED로 전이된다")
    void markAsManualRequired_ShouldChangeStatus() {
        PaymentCompensationModel model = PaymentCompensationModel.create(1L, 100L, "에러");

        model.markAsManualRequired();

        assertThat(model.getStatus()).isEqualTo(CompensationStatus.MANUAL_REQUIRED);
    }
}

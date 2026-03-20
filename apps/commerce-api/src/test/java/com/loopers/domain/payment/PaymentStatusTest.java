package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * U1-7: 각 상태의 허용 전이 목록 검증.
 */
class PaymentStatusTest {

    @DisplayName("REQUESTED는 PENDING, FAILED, UNKNOWN으로 전이 가능하다")
    @Test
    void requested_canTransitionTo_pending_failed_unknown() {
        assertThat(PaymentStatus.REQUESTED.canTransitionTo(PaymentStatus.PENDING)).isTrue();
        assertThat(PaymentStatus.REQUESTED.canTransitionTo(PaymentStatus.FAILED)).isTrue();
        assertThat(PaymentStatus.REQUESTED.canTransitionTo(PaymentStatus.UNKNOWN)).isTrue();
        assertThat(PaymentStatus.REQUESTED.canTransitionTo(PaymentStatus.PAID)).isFalse();
    }

    @DisplayName("PENDING은 PAID, FAILED, UNKNOWN으로 전이 가능하다")
    @Test
    void pending_canTransitionTo_paid_failed_unknown() {
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.PAID)).isTrue();
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.FAILED)).isTrue();
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.UNKNOWN)).isTrue();
        assertThat(PaymentStatus.PENDING.canTransitionTo(PaymentStatus.REQUESTED)).isFalse();
    }

    @DisplayName("PAID는 최종 상태로 어떤 상태로도 전이할 수 없다")
    @Test
    void paid_isTerminal() {
        assertThat(PaymentStatus.PAID.isTerminal()).isTrue();
        assertThat(PaymentStatus.PAID.canTransitionTo(PaymentStatus.FAILED)).isFalse();
        assertThat(PaymentStatus.PAID.canTransitionTo(PaymentStatus.UNKNOWN)).isFalse();
        assertThat(PaymentStatus.PAID.canTransitionTo(PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStatus.PAID.canTransitionTo(PaymentStatus.REQUESTED)).isFalse();
    }

    @DisplayName("FAILED는 최종 상태로 어떤 상태로도 전이할 수 없다")
    @Test
    void failed_isTerminal() {
        assertThat(PaymentStatus.FAILED.isTerminal()).isTrue();
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.PAID)).isFalse();
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.UNKNOWN)).isFalse();
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStatus.FAILED.canTransitionTo(PaymentStatus.REQUESTED)).isFalse();
    }

    @DisplayName("UNKNOWN은 PAID, FAILED로 전이 가능하다")
    @Test
    void unknown_canTransitionTo_paid_failed() {
        assertThat(PaymentStatus.UNKNOWN.canTransitionTo(PaymentStatus.PAID)).isTrue();
        assertThat(PaymentStatus.UNKNOWN.canTransitionTo(PaymentStatus.FAILED)).isTrue();
        assertThat(PaymentStatus.UNKNOWN.canTransitionTo(PaymentStatus.PENDING)).isFalse();
        assertThat(PaymentStatus.UNKNOWN.canTransitionTo(PaymentStatus.REQUESTED)).isFalse();
    }

    @DisplayName("UNKNOWN은 최종 상태가 아니다")
    @Test
    void unknown_isNotTerminal() {
        assertThat(PaymentStatus.UNKNOWN.isTerminal()).isFalse();
    }

    @DisplayName("REQUESTED는 최종 상태가 아니다")
    @Test
    void requested_isNotTerminal() {
        assertThat(PaymentStatus.REQUESTED.isTerminal()).isFalse();
    }
}

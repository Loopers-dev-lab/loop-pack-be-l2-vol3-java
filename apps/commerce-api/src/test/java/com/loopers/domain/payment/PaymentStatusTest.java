package com.loopers.domain.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentStatus 단위 테스트")
class PaymentStatusTest {

    @Test
    @DisplayName("PENDING은 터미널 상태가 아니다")
    void pendingIsNotTerminal() {
        assertThat(PaymentStatus.PENDING.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("SUCCESS는 터미널 상태이다")
    void successIsTerminal() {
        assertThat(PaymentStatus.SUCCESS.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("FAIL은 터미널 상태이다")
    void failIsTerminal() {
        assertThat(PaymentStatus.FAIL.isTerminal()).isTrue();
    }
}

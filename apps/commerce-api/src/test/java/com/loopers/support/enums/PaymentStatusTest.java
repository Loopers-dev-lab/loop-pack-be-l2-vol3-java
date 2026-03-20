package com.loopers.support.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentStatus 열거형 테스트")
class PaymentStatusTest {

    @Test
    @DisplayName("SUCCESS는 최종 상태이다")
    void isTerminal_SUCCESS_ShouldReturnTrue() {
        assertThat(PaymentStatus.SUCCESS.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("FAILED는 최종 상태이다")
    void isTerminal_FAILED_ShouldReturnTrue() {
        assertThat(PaymentStatus.FAILED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("CANCELLED는 최종 상태이다")
    void isTerminal_CANCELLED_ShouldReturnTrue() {
        assertThat(PaymentStatus.CANCELLED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("REQUESTED는 최종 상태가 아니다")
    void isTerminal_REQUESTED_ShouldReturnFalse() {
        assertThat(PaymentStatus.REQUESTED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("REQUESTED에서 SUCCESS로 전이할 수 있다")
    void canTransitionTo_FromREQUESTED_ToSUCCESS_ShouldReturnTrue() {
        assertThat(PaymentStatus.REQUESTED.canTransitionTo(PaymentStatus.SUCCESS)).isTrue();
    }

    @Test
    @DisplayName("REQUESTED에서 FAILED로 전이할 수 있다")
    void canTransitionTo_FromREQUESTED_ToFAILED_ShouldReturnTrue() {
        assertThat(PaymentStatus.REQUESTED.canTransitionTo(PaymentStatus.FAILED)).isTrue();
    }

    @Test
    @DisplayName("REQUESTED에서 CANCELLED로 전이할 수 있다")
    void canTransitionTo_FromREQUESTED_ToCANCELLED_ShouldReturnTrue() {
        assertThat(PaymentStatus.REQUESTED.canTransitionTo(PaymentStatus.CANCELLED)).isTrue();
    }

    @Test
    @DisplayName("SUCCESS에서는 어떤 상태로도 전이할 수 없다")
    void canTransitionTo_FromSUCCESS_ToAny_ShouldReturnFalse() {
        for (PaymentStatus target : PaymentStatus.values()) {
            assertThat(PaymentStatus.SUCCESS.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("FAILED에서는 어떤 상태로도 전이할 수 없다")
    void canTransitionTo_FromFAILED_ToAny_ShouldReturnFalse() {
        for (PaymentStatus target : PaymentStatus.values()) {
            assertThat(PaymentStatus.FAILED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("CANCELLED에서는 어떤 상태로도 전이할 수 없다")
    void canTransitionTo_FromCANCELLED_ToAny_ShouldReturnFalse() {
        for (PaymentStatus target : PaymentStatus.values()) {
            assertThat(PaymentStatus.CANCELLED.canTransitionTo(target)).isFalse();
        }
    }

    @Test
    @DisplayName("REQUESTED, SUCCESS, FAILED, CANCELLED 4개 값이 존재한다")
    void values_ShouldContain_AllFourStatuses() {
        assertThat(PaymentStatus.values())
                .containsExactlyInAnyOrder(
                        PaymentStatus.REQUESTED,
                        PaymentStatus.SUCCESS,
                        PaymentStatus.FAILED,
                        PaymentStatus.CANCELLED
                );
    }
}

package com.loopers.support.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("QueueStatus 열거형 테스트")
class QueueStatusTest {

    @Test
    @DisplayName("WAITING, READY, NOT_IN_QUEUE 값이 존재한다")
    void values_ShouldContain_AllStatuses() {
        assertThat(QueueStatus.values())
                .containsExactlyInAnyOrder(
                        QueueStatus.WAITING,
                        QueueStatus.READY,
                        QueueStatus.NOT_IN_QUEUE
                );
    }

    // --- evaluate() 정적 팩토리 ---

    @Test
    @DisplayName("대기열 O, 토큰 X → WAITING")
    void evaluate_InQueueNoToken_ShouldReturnWaiting() {
        assertThat(QueueStatus.evaluate(true, false)).isEqualTo(QueueStatus.WAITING);
    }

    @Test
    @DisplayName("대기열 X, 토큰 O → READY")
    void evaluate_NotInQueueWithToken_ShouldReturnReady() {
        assertThat(QueueStatus.evaluate(false, true)).isEqualTo(QueueStatus.READY);
    }

    @Test
    @DisplayName("대기열 X, 토큰 X → NOT_IN_QUEUE")
    void evaluate_BothAbsent_ShouldReturnNotInQueue() {
        assertThat(QueueStatus.evaluate(false, false)).isEqualTo(QueueStatus.NOT_IN_QUEUE);
    }

    @Test
    @DisplayName("대기열 O, 토큰 O → READY (비정상이지만 토큰 우선)")
    void evaluate_BothPresent_ShouldReturnReady() {
        assertThat(QueueStatus.evaluate(true, true)).isEqualTo(QueueStatus.READY);
    }

    // --- canOrder() ---

    @Test
    @DisplayName("READY일 때 canOrder()은 true를 반환한다")
    void canOrder_Ready_ShouldReturnTrue() {
        assertThat(QueueStatus.READY.canOrder()).isTrue();
    }

    @Test
    @DisplayName("WAITING일 때 canOrder()은 false를 반환한다")
    void canOrder_Waiting_ShouldReturnFalse() {
        assertThat(QueueStatus.WAITING.canOrder()).isFalse();
    }

    @Test
    @DisplayName("NOT_IN_QUEUE일 때 canOrder()은 false를 반환한다")
    void canOrder_NotInQueue_ShouldReturnFalse() {
        assertThat(QueueStatus.NOT_IN_QUEUE.canOrder()).isFalse();
    }

    // --- shouldPoll() ---

    @Test
    @DisplayName("WAITING일 때 shouldPoll()은 true를 반환한다")
    void shouldPoll_Waiting_ShouldReturnTrue() {
        assertThat(QueueStatus.WAITING.shouldPoll()).isTrue();
    }

    @Test
    @DisplayName("READY일 때 shouldPoll()은 false를 반환한다 (Polling 중단, 주문으로 전환)")
    void shouldPoll_Ready_ShouldReturnFalse() {
        assertThat(QueueStatus.READY.shouldPoll()).isFalse();
    }

    @Test
    @DisplayName("NOT_IN_QUEUE일 때 shouldPoll()은 false를 반환한다")
    void shouldPoll_NotInQueue_ShouldReturnFalse() {
        assertThat(QueueStatus.NOT_IN_QUEUE.shouldPoll()).isFalse();
    }
}

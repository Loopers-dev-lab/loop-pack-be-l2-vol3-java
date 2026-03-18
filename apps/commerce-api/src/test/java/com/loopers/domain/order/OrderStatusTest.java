package com.loopers.domain.order;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("OrderStatus Enum 테스트")
class OrderStatusTest {

    @Test
    @DisplayName("PENDING에서 PAID로 전이 가능")
    void canTransition_pendingToPaid() {
        // given
        OrderStatus status = OrderStatus.PENDING;

        // when
        boolean canTransition = status.canTransitionTo(OrderStatus.PAID);

        // then
        assertThat(canTransition).isTrue();
    }

    @Test
    @DisplayName("PAID에서 어떤 상태로도 전이 불가")
    void cannotTransition_fromPaid() {
        // given
        OrderStatus status = OrderStatus.PAID;

        // when & then
        assertThat(status.canTransitionTo(OrderStatus.PENDING)).isFalse();
        assertThat(status.canTransitionTo(OrderStatus.PAID)).isFalse();
        assertThat(status.canTransitionTo(OrderStatus.CANCELED)).isFalse();
    }

    @Test
    @DisplayName("PAID 상태에서 전이 시도 시 예외 발생")
    void validateTransition_fromPaid_throwsException() {
        // given
        OrderStatus status = OrderStatus.PAID;

        // when & then
        assertThatThrownBy(() -> status.validateTransition(OrderStatus.CANCELED))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("PENDING에서 CANCELED로 전이 가능")
    void canTransition_pendingToCanceled() {
        // given
        OrderStatus status = OrderStatus.PENDING;

        // when
        boolean canTransition = status.canTransitionTo(OrderStatus.CANCELED);

        // then
        assertThat(canTransition).isTrue();
    }

    @Test
    @DisplayName("CANCELED에서 CANCELED로 전이 가능 (멱등성)")
    void canTransition_canceledToCanceled() {
        // given
        OrderStatus status = OrderStatus.CANCELED;

        // when
        boolean canTransition = status.canTransitionTo(OrderStatus.CANCELED);

        // then
        assertThat(canTransition).isTrue();
    }

    @Test
    @DisplayName("CANCELED에서 PENDING으로 전이 불가")
    void cannotTransition_canceledToPending() {
        // given
        OrderStatus status = OrderStatus.CANCELED;

        // when
        boolean canTransition = status.canTransitionTo(OrderStatus.PENDING);

        // then
        assertThat(canTransition).isFalse();
    }

    @Test
    @DisplayName("불가능한 상태 전이 시 예외 발생")
    void validateTransition_throwsException() {
        // given
        OrderStatus status = OrderStatus.CANCELED;

        // when & then
        assertThatThrownBy(() -> status.validateTransition(OrderStatus.PENDING))
                .isInstanceOf(CoreException.class);
    }
}

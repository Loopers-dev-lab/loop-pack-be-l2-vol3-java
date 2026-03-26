package com.loopers.support.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderStatus 열거형 테스트")
class OrderStatusTest {

    @Test
    @DisplayName("PENDING_PAYMENT, PAID, CANCELLED, EXPIRED 값이 존재한다")
    void values_ShouldContain_AllStatuses() {
        assertThat(OrderStatus.values())
                .containsExactlyInAnyOrder(
                        OrderStatus.PENDING_PAYMENT,
                        OrderStatus.PAID,
                        OrderStatus.CANCELLED,
                        OrderStatus.EXPIRED
                );
    }

    @Test
    @DisplayName("PENDING_PAYMENT일 때 canCancel()은 true를 반환한다")
    void canCancel_PendingPayment_ShouldReturnTrue() {
        assertThat(OrderStatus.PENDING_PAYMENT.canCancel()).isTrue();
    }

    @Test
    @DisplayName("PAID일 때 canCancel()은 false를 반환한다")
    void canCancel_Paid_ShouldReturnFalse() {
        assertThat(OrderStatus.PAID.canCancel()).isFalse();
    }

    @Test
    @DisplayName("CANCELLED일 때 canCancel()은 false를 반환한다")
    void canCancel_Cancelled_ShouldReturnFalse() {
        assertThat(OrderStatus.CANCELLED.canCancel()).isFalse();
    }

    @Test
    @DisplayName("EXPIRED일 때 canCancel()은 false를 반환한다")
    void canCancel_Expired_ShouldReturnFalse() {
        assertThat(OrderStatus.EXPIRED.canCancel()).isFalse();
    }
}

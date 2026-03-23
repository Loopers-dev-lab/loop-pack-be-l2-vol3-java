package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class OrderStatusHistoryTest {

    private static final Long DEFAULT_ORDER_ID = 1L;
    private static final ZonedDateTime DEFAULT_CHANGED_AT = ZonedDateTime.now();

    private static void assertCoreException(Runnable runnable, String message) {
        assertThatThrownBy(runnable::run)
            .isInstanceOf(CoreException.class)
            .hasMessage(message);
    }

    @Nested
    @DisplayName("주문 상태 이력 생성")
    class Create {

        @Test
        @DisplayName("주문 상태 이력 생성에 성공한다")
        void success_create_order_status_history() {
            OrderStatusHistory history = OrderStatusHistory.create(
                null, DEFAULT_ORDER_ID, OrderStatus.PENDING, DEFAULT_CHANGED_AT
            );

            assertThat(history.refOrderId()).isEqualTo(DEFAULT_ORDER_ID);
            assertThat(history.status()).isEqualTo(OrderStatus.PENDING);
            assertThat(history.changedAt()).isEqualTo(DEFAULT_CHANGED_AT);
        }

        @DisplayName("주문FK가 유효하지 않으면, 생성시 예외를 던진다")
        @ParameterizedTest
        @ValueSource(longs = {-1L, 0L})
        void fail_when_invalid_ref_order_id(Long refOrderId) {
            assertCoreException(
                () -> OrderStatusHistory.create(null, refOrderId, OrderStatus.PENDING, DEFAULT_CHANGED_AT),
                "주문FK는 null이거나 0이하가 될 수 없습니다"
            );
        }

        @Test
        @DisplayName("주문 상태가 null이면, 생성시 예외를 던진다")
        void fail_when_status_is_null() {
            assertCoreException(
                () -> OrderStatusHistory.create(null, DEFAULT_ORDER_ID, null, DEFAULT_CHANGED_AT),
                "주문 상태는 필수입니다"
            );
        }

        @Test
        @DisplayName("주문 상태 변경 일시가 null이면, 생성시 예외를 던진다")
        void fail_when_changed_at_is_null() {
            assertCoreException(
                () -> OrderStatusHistory.create(null, DEFAULT_ORDER_ID, OrderStatus.PENDING, null),
                "주문 상태 변경 일시는 필수입니다"
            );
        }
    }
}

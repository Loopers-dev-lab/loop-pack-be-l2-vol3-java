package com.loopers.domain.order;

import com.loopers.domain.BaseStringIdEntity;
import com.loopers.support.enums.OrderStatus;
import com.loopers.support.enums.OrderType;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.*;

@DisplayName("OrderModel 도메인 모델 테스트")
class OrderModelTest {

    @Nested
    @DisplayName("생성 검증")
    class CreateTests {

        @Test
        @DisplayName("유효한 입력으로 생성 성공")
        void create_WithValidInputs_ShouldSuccess() {
            OrderModel order = createTestOrder();

            assertThat(order.getUserId()).isEqualTo("user-001");
            assertThat(order.getOrderType()).isEqualTo(OrderType.DIRECT);
            assertThat(order.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(30000));
        }

        @Test
        @DisplayName("생성 시 상태는 PENDING_PAYMENT이다")
        void create_ShouldSetStatus_PENDING_PAYMENT() {
            OrderModel order = createTestOrder();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        }

        @Test
        @DisplayName("생성 시 expiresAt이 15분 후로 설정된다")
        void create_ShouldSetExpiresAt() {
            OrderModel order = createTestOrder();
            assertThat(order.getExpiresAt()).isNotNull();
            assertThat(order.getExpiresAt()).isAfter(java.time.LocalDateTime.now().plusMinutes(14));
            assertThat(order.getExpiresAt()).isBefore(java.time.LocalDateTime.now().plusMinutes(16));
        }

        @Test
        @DisplayName("userId가 null이면 CoreException 발생")
        void create_WithNullUserId_ShouldThrow() {
            assertThatThrownBy(() -> OrderModel.create(null, OrderType.DIRECT, BigDecimal.valueOf(10000)))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("BaseStringIdEntity를 상속한다")
        void create_ShouldExtendBaseStringIdEntity() {
            OrderModel order = createTestOrder();
            assertThat(order).isInstanceOf(BaseStringIdEntity.class);
        }
    }

    @Nested
    @DisplayName("취소")
    class CancelTests {

        @Test
        @DisplayName("PENDING_PAYMENT 상태에서 취소 성공")
        void cancel_WhenPendingPayment_ShouldSetStatus_CANCELLED() {
            OrderModel order = createTestOrder();
            order.cancel();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("이미 취소된 상태에서 취소는 멱등하다")
        void cancel_WhenAlreadyCancelled_ShouldBeIdempotent() {
            OrderModel order = createTestOrder();
            order.cancel();
            assertThatCode(() -> order.cancel()).doesNotThrowAnyException();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("만료된 주문 취소 시 CoreException 발생")
        void cancel_WhenExpired_ShouldThrow() {
            OrderModel order = createTestOrder();
            order.expire();
            assertThatThrownBy(() -> order.cancel())
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("만료")
    class ExpireTests {

        @Test
        @DisplayName("PENDING_PAYMENT 상태에서 만료 성공")
        void expire_WhenPendingPayment_ShouldSetStatus_EXPIRED() {
            OrderModel order = createTestOrder();
            order.expire();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        }

        @Test
        @DisplayName("이미 만료된 상태에서 만료는 멱등하다")
        void expire_WhenAlreadyExpired_ShouldBeIdempotent() {
            OrderModel order = createTestOrder();
            order.expire();
            assertThatCode(() -> order.expire()).doesNotThrowAnyException();
            assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
        }

        @Test
        @DisplayName("취소된 주문 만료 시 CoreException 발생")
        void expire_WhenCancelled_ShouldThrow() {
            OrderModel order = createTestOrder();
            order.cancel();
            assertThatThrownBy(() -> order.expire())
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("조건 검사")
    class ConditionTests {

        @Test
        @DisplayName("PENDING_PAYMENT이고 만료 전이면 canCancel true")
        void canCancel_WhenPendingPaymentAndNotExpired_True() {
            OrderModel order = createTestOrder();
            assertThat(order.canCancel()).isTrue();
        }

        @Test
        @DisplayName("CANCELLED이면 canCancel false")
        void canCancel_WhenCancelled_False() {
            OrderModel order = createTestOrder();
            order.cancel();
            assertThat(order.canCancel()).isFalse();
        }

        @Test
        @DisplayName("EXPIRED이면 canCancel false")
        void canCancel_WhenExpired_False() {
            OrderModel order = createTestOrder();
            order.expire();
            assertThat(order.canCancel()).isFalse();
        }

        @Test
        @DisplayName("expiresAt이 과거이면 isTimeExpired true")
        void isExpired_WhenExpiresAtPast_True() {
            // expiresAt은 15분 후이므로 새 주문은 만료 전
            OrderModel order = createTestOrder();
            assertThat(order.isTimeExpired()).isFalse();
        }

        @Test
        @DisplayName("expiresAt이 미래이면 isTimeExpired false")
        void isExpired_WhenExpiresAtFuture_False() {
            OrderModel order = createTestOrder();
            assertThat(order.isTimeExpired()).isFalse();
        }
    }

    // === Helper ===

    private OrderModel createTestOrder() {
        return OrderModel.create("user-001", OrderType.DIRECT, BigDecimal.valueOf(30000));
    }
}

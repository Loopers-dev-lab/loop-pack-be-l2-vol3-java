package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CouponPendingActionModel 단위 테스트")
class CouponPendingActionModelTest {

    @Nested
    @DisplayName("팩토리 메서드")
    class FactoryMethodTests {

        @Test
        @DisplayName("confirm() 호출 시 CONFIRM 타입의 PENDING 액션이 생성된다")
        void confirm_ShouldCreateConfirmAction() {
            CouponPendingActionModel action = CouponPendingActionModel.confirm(100L, 200L);

            assertThat(action.getActionType()).isEqualTo(CouponActionType.CONFIRM);
            assertThat(action.getUserCouponId()).isEqualTo(100L);
            assertThat(action.getOrderId()).isEqualTo(200L);
            assertThat(action.getStatus()).isEqualTo(CouponActionStatus.PENDING);
            assertThat(action.getRetryCount()).isZero();
            assertThat(action.getCreatedAt()).isNotNull();
            assertThat(action.getProcessedAt()).isNull();
            assertThat(action.getErrorMessage()).isNull();
        }

        @Test
        @DisplayName("restore() 호출 시 RESTORE 타입의 PENDING 액션이 생성된다")
        void restore_ShouldCreateRestoreAction() {
            CouponPendingActionModel action = CouponPendingActionModel.restore(100L, 200L);

            assertThat(action.getActionType()).isEqualTo(CouponActionType.RESTORE);
            assertThat(action.getUserCouponId()).isEqualTo(100L);
            assertThat(action.getOrderId()).isEqualTo(200L);
            assertThat(action.getStatus()).isEqualTo(CouponActionStatus.PENDING);
            assertThat(action.getRetryCount()).isZero();
        }
    }

    @Nested
    @DisplayName("상태 전이")
    class StateTransitionTests {

        @Test
        @DisplayName("markDone() 호출 시 DONE 상태로 전이되고 processedAt이 설정된다")
        void markDone_ShouldTransitionToDone() {
            CouponPendingActionModel action = CouponPendingActionModel.confirm(1L, 1L);

            action.markDone();

            assertThat(action.getStatus()).isEqualTo(CouponActionStatus.DONE);
            assertThat(action.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("markFailed() 호출 시 FAILED 상태로 전이되고 에러 메시지가 저장된다")
        void markFailed_ShouldTransitionToFailed() {
            CouponPendingActionModel action = CouponPendingActionModel.confirm(1L, 1L);

            action.markFailed("쿠폰 사용 확정 실패");

            assertThat(action.getStatus()).isEqualTo(CouponActionStatus.FAILED);
            assertThat(action.getProcessedAt()).isNotNull();
            assertThat(action.getErrorMessage()).isEqualTo("쿠폰 사용 확정 실패");
        }

        @Test
        @DisplayName("markCancelled() 호출 시 CANCELLED 상태로 전이된다")
        void markCancelled_ShouldTransitionToCancelled() {
            CouponPendingActionModel action = CouponPendingActionModel.confirm(1L, 1L);

            action.markCancelled();

            assertThat(action.getStatus()).isEqualTo(CouponActionStatus.CANCELLED);
            assertThat(action.getProcessedAt()).isNotNull();
        }

        @Test
        @DisplayName("incrementRetry() 호출 시 retryCount가 1 증가한다")
        void incrementRetry_ShouldIncrementCount() {
            CouponPendingActionModel action = CouponPendingActionModel.confirm(1L, 1L);

            action.incrementRetry();
            assertThat(action.getRetryCount()).isEqualTo(1);

            action.incrementRetry();
            assertThat(action.getRetryCount()).isEqualTo(2);
        }
    }
}

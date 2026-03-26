package com.loopers.batch;

import com.loopers.domain.coupon.CouponActionStatus;
import com.loopers.domain.coupon.CouponActionType;
import com.loopers.domain.coupon.CouponPendingActionModel;
import com.loopers.domain.coupon.CouponPendingActionRepository;
import com.loopers.domain.coupon.CouponService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponActionProcessor 단위 테스트")
class CouponActionProcessorTest {

    @Mock
    CouponService couponService;

    @Mock
    CouponPendingActionRepository couponPendingActionRepository;

    @InjectMocks
    CouponActionProcessor couponActionProcessor;

    @Nested
    @DisplayName("CONFIRM 액션 처리")
    class ConfirmActionTests {

        @Test
        @DisplayName("성공 시 confirmCouponUsed가 호출되고 DONE 상태로 전이된다")
        void process_ConfirmSuccess_ShouldMarkDone() {
            CouponPendingActionModel action = CouponPendingActionModel.confirm(100L, 200L);

            couponActionProcessor.process(action);

            verify(couponService).confirmCouponUsed(100L, 200L);
            assertThat(action.getStatus()).isEqualTo(CouponActionStatus.DONE);
            assertThat(action.getRetryCount()).isEqualTo(1);
            verify(couponPendingActionRepository).save(action);
        }

        @Test
        @DisplayName("실패 시 retryCount가 증가하고 상태는 PENDING을 유지한다")
        void process_ConfirmFailure_ShouldIncrementRetry() {
            CouponPendingActionModel action = CouponPendingActionModel.confirm(100L, 200L);
            doThrow(new RuntimeException("DB error"))
                    .when(couponService).confirmCouponUsed(100L, 200L);

            couponActionProcessor.process(action);

            assertThat(action.getRetryCount()).isEqualTo(1);
            assertThat(action.getStatus()).isEqualTo(CouponActionStatus.PENDING);
            verify(couponPendingActionRepository).save(action);
        }

        @Test
        @DisplayName("5회 연속 실패 시 FAILED 상태로 전이된다")
        void process_ConfirmMaxRetry_ShouldMarkFailed() {
            CouponPendingActionModel action = CouponPendingActionModel.confirm(100L, 200L);
            doThrow(new RuntimeException("DB error"))
                    .when(couponService).confirmCouponUsed(100L, 200L);

            // 5번 처리 시도
            for (int i = 0; i < 5; i++) {
                couponActionProcessor.process(action);
            }

            assertThat(action.getRetryCount()).isEqualTo(5);
            assertThat(action.getStatus()).isEqualTo(CouponActionStatus.FAILED);
            assertThat(action.getErrorMessage()).isNotNull();
            verify(couponPendingActionRepository, times(5)).save(action);
        }
    }

    @Nested
    @DisplayName("RESTORE 액션 처리")
    class RestoreActionTests {

        @Test
        @DisplayName("성공 시 restoreCouponByAction이 호출되고 DONE 상태로 전이된다")
        void process_RestoreSuccess_ShouldMarkDone() {
            CouponPendingActionModel action = CouponPendingActionModel.restore(100L, 200L);

            couponActionProcessor.process(action);

            verify(couponService).restoreCouponByAction(100L);
            assertThat(action.getStatus()).isEqualTo(CouponActionStatus.DONE);
            assertThat(action.getRetryCount()).isEqualTo(1);
            verify(couponPendingActionRepository).save(action);
        }
    }
}

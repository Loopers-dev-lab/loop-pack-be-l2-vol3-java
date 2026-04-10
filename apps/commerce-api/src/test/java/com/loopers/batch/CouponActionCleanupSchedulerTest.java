package com.loopers.batch;

import com.loopers.domain.coupon.CouponPendingActionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponActionCleanupScheduler 단위 테스트")
class CouponActionCleanupSchedulerTest {

    @Mock
    CouponPendingActionRepository couponPendingActionRepository;

    @InjectMocks
    CouponActionCleanupScheduler couponActionCleanupScheduler;

    @Test
    @DisplayName("cleanup 호출 시 3일 이전 DONE 액션을 배치 삭제한다")
    void cleanup_ShouldDeleteDoneActionsOlderThanThreshold() {
        when(couponPendingActionRepository.deleteDoneOlderThan(any(LocalDateTime.class)))
                .thenReturn(10);

        couponActionCleanupScheduler.cleanup();

        verify(couponPendingActionRepository).deleteDoneOlderThan(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("삭제할 데이터가 없어도 deleteDoneOlderThan은 1회 호출된다")
    void cleanup_NoData_ShouldStillCallDelete() {
        when(couponPendingActionRepository.deleteDoneOlderThan(any(LocalDateTime.class)))
                .thenReturn(0);

        couponActionCleanupScheduler.cleanup();

        verify(couponPendingActionRepository, times(1)).deleteDoneOlderThan(any(LocalDateTime.class));
    }
}

package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponPendingActionService 단위 테스트")
class CouponPendingActionServiceTest {

    @Mock
    CouponPendingActionRepository couponPendingActionRepository;

    @InjectMocks
    CouponPendingActionService couponPendingActionService;

    @Test
    @DisplayName("saveConfirm 호출 시 CONFIRM 타입 액션이 저장된다")
    void saveConfirm_ShouldSaveConfirmAction() {
        when(couponPendingActionRepository.save(any(CouponPendingActionModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CouponPendingActionModel result = couponPendingActionService.saveConfirm(100L, 200L);

        assertThat(result.getActionType()).isEqualTo(CouponActionType.CONFIRM);
        assertThat(result.getUserCouponId()).isEqualTo(100L);
        assertThat(result.getOrderId()).isEqualTo(200L);
        verify(couponPendingActionRepository).save(argThat(action ->
                action.getActionType() == CouponActionType.CONFIRM));
    }

    @Test
    @DisplayName("saveRestore 호출 시 RESTORE 타입 액션이 저장된다")
    void saveRestore_ShouldSaveRestoreAction() {
        when(couponPendingActionRepository.save(any(CouponPendingActionModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        CouponPendingActionModel result = couponPendingActionService.saveRestore(100L, 200L);

        assertThat(result.getActionType()).isEqualTo(CouponActionType.RESTORE);
        assertThat(result.getUserCouponId()).isEqualTo(100L);
        assertThat(result.getOrderId()).isEqualTo(200L);
        verify(couponPendingActionRepository).save(argThat(action ->
                action.getActionType() == CouponActionType.RESTORE));
    }

    @Test
    @DisplayName("findPending 호출 시 limit만큼 PENDING 액션을 반환한다")
    void findPending_ShouldReturnPendingActions() {
        CouponPendingActionModel action1 = CouponPendingActionModel.confirm(1L, 1L);
        CouponPendingActionModel action2 = CouponPendingActionModel.restore(2L, 2L);
        when(couponPendingActionRepository.findPending(50)).thenReturn(List.of(action1, action2));

        List<CouponPendingActionModel> result = couponPendingActionService.findPending(50);

        assertThat(result).hasSize(2);
        verify(couponPendingActionRepository).findPending(50);
    }

    @Test
    @DisplayName("cancelPendingConfirms 호출 시 repository에 위임한다")
    void cancelPendingConfirms_ShouldDelegateToRepository() {
        when(couponPendingActionRepository.cancelPendingConfirmsByUserCouponId(100L)).thenReturn(2);

        int cancelled = couponPendingActionService.cancelPendingConfirms(100L);

        assertThat(cancelled).isEqualTo(2);
        verify(couponPendingActionRepository).cancelPendingConfirmsByUserCouponId(100L);
    }
}

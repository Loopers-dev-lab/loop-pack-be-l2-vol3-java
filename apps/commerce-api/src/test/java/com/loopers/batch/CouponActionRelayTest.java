package com.loopers.batch;

import com.loopers.domain.coupon.CouponPendingActionModel;
import com.loopers.domain.coupon.CouponPendingActionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponActionRelay 단위 테스트")
class CouponActionRelayTest {

    @Mock
    CouponPendingActionService couponPendingActionService;

    @Mock
    CouponActionProcessor couponActionProcessor;

    @InjectMocks
    CouponActionRelay couponActionRelay;

    @Test
    @DisplayName("PENDING 액션이 있으면 processor에 위임한다")
    void relay_WithPendingActions_ShouldDelegateToProcessor() {
        CouponPendingActionModel action1 = CouponPendingActionModel.confirm(1L, 1L);
        CouponPendingActionModel action2 = CouponPendingActionModel.restore(2L, 2L);
        when(couponPendingActionService.findPending(anyInt())).thenReturn(List.of(action1, action2));

        couponActionRelay.relay();

        verify(couponActionProcessor).process(action1);
        verify(couponActionProcessor).process(action2);
    }

    @Test
    @DisplayName("PENDING 액션이 없으면 processor가 호출되지 않는다")
    void relay_NoPendingActions_ShouldNotCallProcessor() {
        when(couponPendingActionService.findPending(anyInt())).thenReturn(List.of());

        couponActionRelay.relay();

        verify(couponActionProcessor, never()).process(any());
    }

    @Test
    @DisplayName("processor에서 예외 발생 시 다음 액션을 계속 처리한다")
    void relay_ProcessorException_ShouldContinueToNext() {
        CouponPendingActionModel action1 = CouponPendingActionModel.confirm(1L, 1L);
        CouponPendingActionModel action2 = CouponPendingActionModel.restore(2L, 2L);
        when(couponPendingActionService.findPending(anyInt())).thenReturn(List.of(action1, action2));
        doThrow(new RuntimeException("unexpected error")).when(couponActionProcessor).process(action1);

        couponActionRelay.relay();

        verify(couponActionProcessor).process(action1);
        verify(couponActionProcessor).process(action2);
    }
}

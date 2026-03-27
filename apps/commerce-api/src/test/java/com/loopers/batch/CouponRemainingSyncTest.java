package com.loopers.batch;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRemainingCache;
import com.loopers.domain.coupon.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponRemainingSync 단위 테스트")
class CouponRemainingSyncTest {

    @Mock
    CouponRepository couponRepository;

    @Mock
    CouponRemainingCache couponRemainingCache;

    @InjectMocks
    CouponRemainingSync couponRemainingSync;

    private CouponModel rushCoupon;

    @BeforeEach
    void setUp() {
        rushCoupon = mock(CouponModel.class);
        when(rushCoupon.isRushCoupon()).thenReturn(true);
        when(rushCoupon.isDeleted()).thenReturn(false);
        when(rushCoupon.getCouponId()).thenReturn(1L);
        when(rushCoupon.getMaxQuantity()).thenReturn(100);
        when(rushCoupon.getIssuedCount()).thenReturn(50);
    }

    @Test
    @DisplayName("Redis 값과 DB 잔여 수량이 다르면 set 호출한다")
    void syncRemainingFromDb_Mismatch_ShouldSetRedisValue() {
        // given
        when(couponRepository.findAll()).thenReturn(List.of(rushCoupon));
        when(couponRemainingCache.getRemaining(1L)).thenReturn("40");

        // when
        couponRemainingSync.syncRemainingFromDb();

        // then
        verify(couponRemainingCache).setRemaining(1L, "50");
    }

    @Test
    @DisplayName("Redis 값과 DB 잔여 수량이 같으면 set 호출하지 않는다")
    void syncRemainingFromDb_Match_ShouldNeverSet() {
        // given
        when(couponRepository.findAll()).thenReturn(List.of(rushCoupon));
        when(couponRemainingCache.getRemaining(1L)).thenReturn("50");

        // when
        couponRemainingSync.syncRemainingFromDb();

        // then
        verify(couponRemainingCache, never()).setRemaining(anyLong(), anyString());
    }
}

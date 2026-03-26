package com.loopers.batch;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponRemainingSync 단위 테스트")
class CouponRemainingSyncTest {

    @Mock
    CouponRepository couponRepository;

    @Mock
    StringRedisTemplate stringRedisTemplate;

    @Mock
    ValueOperations<String, String> valueOps;

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
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("coupon:remaining:1")).thenReturn("40");

        // when
        couponRemainingSync.syncRemainingFromDb();

        // then
        verify(valueOps).set("coupon:remaining:1", "50");
    }

    @Test
    @DisplayName("Redis 값과 DB 잔여 수량이 같으면 set 호출하지 않는다")
    void syncRemainingFromDb_Match_ShouldNeverSet() {
        // given
        when(couponRepository.findAll()).thenReturn(List.of(rushCoupon));
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("coupon:remaining:1")).thenReturn("50");

        // when
        couponRemainingSync.syncRemainingFromDb();

        // then
        verify(valueOps, never()).set(anyString(), anyString());
    }
}

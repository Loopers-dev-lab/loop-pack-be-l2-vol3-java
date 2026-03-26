package com.loopers.application;

import com.loopers.domain.coupon.CouponIssueResultService;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCouponService;
import com.loopers.domain.coupon.event.CouponIssueMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CouponIssueProcessorUnitTest {

    @Mock
    private CouponService couponService;

    @Mock
    private UserCouponService userCouponService;

    @Mock
    private CouponIssueResultService couponIssueResultService;

    @InjectMocks
    private CouponIssueProcessor processor;

    @DisplayName("쿠폰 발급을 처리할 때,")
    @Nested
    class Process {

        @DisplayName("정상 발급이면, 수량 차감 + 유저쿠폰 생성 + 결과 SUCCESS 처리된다")
        @Test
        void processSuccess() {
            // given
            CouponIssueMessage message = new CouponIssueMessage(
                    "req-1", 1L, 100L, LocalDateTime.now());
            CouponModel coupon = mock(CouponModel.class);

            when(userCouponService.existsByCouponIdAndMemberId(1L, 100L)).thenReturn(false);
            when(couponService.getByIdWithLock(1L)).thenReturn(coupon);
            when(coupon.getRemainingQuantity()).thenReturn(10);

            // when
            processor.process(message);

            // then
            verify(coupon).increaseIssuedQuantity();
            verify(userCouponService).issue(1L, 100L);
            verify(couponIssueResultService).markSuccess("req-1");
        }

        @DisplayName("이미 발급된 쿠폰이면, FAILED 처리되고 수량 차감이 발생하지 않는다")
        @Test
        void processDuplicateFailed() {
            // given
            CouponIssueMessage message = new CouponIssueMessage(
                    "req-1", 1L, 100L, LocalDateTime.now());
            when(userCouponService.existsByCouponIdAndMemberId(1L, 100L)).thenReturn(true);

            // when
            processor.process(message);

            // then
            verify(couponIssueResultService).markFailed("req-1", "이미 발급된 쿠폰입니다");
            verify(couponService, never()).getByIdWithLock(anyLong());
            verify(userCouponService, never()).issue(anyLong(), anyLong());
        }

        @DisplayName("수량이 소진되면, FAILED 처리되고 유저쿠폰이 생성되지 않는다")
        @Test
        void processSoldOutFailed() {
            // given
            CouponIssueMessage message = new CouponIssueMessage(
                    "req-1", 1L, 100L, LocalDateTime.now());
            CouponModel coupon = mock(CouponModel.class);

            when(userCouponService.existsByCouponIdAndMemberId(1L, 100L)).thenReturn(false);
            when(couponService.getByIdWithLock(1L)).thenReturn(coupon);
            when(coupon.getRemainingQuantity()).thenReturn(0);

            // when
            processor.process(message);

            // then
            verify(couponIssueResultService).markFailed("req-1", "쿠폰이 모두 소진되었습니다");
            verify(coupon, never()).increaseIssuedQuantity();
            verify(userCouponService, never()).issue(anyLong(), anyLong());
        }
    }
}

package com.loopers.application.coupon;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.OwnedCoupon;
import com.loopers.domain.coupon.OwnedCouponRepository;
import com.loopers.domain.eventhandled.EventHandledRepository;

@ExtendWith(MockitoExtension.class)
class CouponIssueServiceTest {

    @InjectMocks
    private CouponIssueService couponIssueService;

    @Mock
    private EventHandledRepository eventHandledRepository;

    @Mock
    private CouponRepository couponRepository;

    @Mock
    private OwnedCouponRepository ownedCouponRepository;

    @Mock
    private Coupon coupon;

    @DisplayName("쿠폰 발급을 처리할 때,")
    @Nested
    class Issue {

        @DisplayName("신규 이벤트이면, 쿠폰을 발급하고 저장한다.")
        @Test
        void issuesAndSaves_whenNewEvent() {
            // arrange
            given(eventHandledRepository.markIfAbsent("event-1")).willReturn(true);
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));
            given(ownedCouponRepository.existsByCouponAndUserId(coupon, 100L)).willReturn(false);

            // act
            couponIssueService.issue("event-1", 1L, 100L);

            // assert
            then(coupon).should().issue();
            then(ownedCouponRepository).should().save(any(OwnedCoupon.class));
        }

        @DisplayName("중복 이벤트이면, 아무 처리도 하지 않는다.")
        @Test
        void skips_whenDuplicateEvent() {
            // arrange
            given(eventHandledRepository.markIfAbsent("dup-event")).willReturn(false);

            // act
            couponIssueService.issue("dup-event", 1L, 100L);

            // assert
            then(couponRepository).shouldHaveNoInteractions();
            then(ownedCouponRepository).shouldHaveNoInteractions();
        }

        @DisplayName("이미 발급된 쿠폰이면, 발급하지 않는다.")
        @Test
        void skips_whenAlreadyIssued() {
            // arrange
            given(eventHandledRepository.markIfAbsent("event-1")).willReturn(true);
            given(couponRepository.findById(1L)).willReturn(Optional.of(coupon));
            given(ownedCouponRepository.existsByCouponAndUserId(coupon, 100L)).willReturn(true);

            // act
            couponIssueService.issue("event-1", 1L, 100L);

            // assert
            then(coupon).shouldHaveNoInteractions();
            then(ownedCouponRepository).should().existsByCouponAndUserId(coupon, 100L);
            then(ownedCouponRepository).shouldHaveNoMoreInteractions();
        }

        @DisplayName("쿠폰이 존재하지 않으면, 예외가 발생한다.")
        @Test
        void throwsException_whenCouponNotFound() {
            // arrange
            given(eventHandledRepository.markIfAbsent("event-1")).willReturn(true);
            given(couponRepository.findById(999L)).willReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> couponIssueService.issue("event-1", 999L, 100L))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}

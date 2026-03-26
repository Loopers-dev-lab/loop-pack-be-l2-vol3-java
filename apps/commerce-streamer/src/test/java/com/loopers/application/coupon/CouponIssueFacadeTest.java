package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.infrastructure.eventhandled.EventHandled;
import com.loopers.infrastructure.eventhandled.EventHandledJpaRepository;
import com.loopers.interfaces.consumer.payload.CouponIssuePayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponIssueFacadeTest {

    CouponRepository couponRepository = mock(CouponRepository.class);
    UserCouponRepository userCouponRepository = mock(UserCouponRepository.class);
    CouponIssueRequestRepository couponIssueRequestRepository = mock(CouponIssueRequestRepository.class);
    EventHandledJpaRepository eventHandledJpaRepository = mock(EventHandledJpaRepository.class);

    CouponIssueFacade facade = new CouponIssueFacade(
            couponRepository,
            userCouponRepository,
            couponIssueRequestRepository,
            eventHandledJpaRepository
    );

    @DisplayName("processIssue() 를 호출할 때, ")
    @Nested
    class ProcessIssue {

        @DisplayName("이미 처리된 eventId 이면, 아무 처리도 하지 않는다.")
        @Test
        void doesNothing_whenEventAlreadyHandled() {
            // arrange
            CouponIssuePayload payload = new CouponIssuePayload("dup-event-id", "req-1", 1L, 2L);
            when(eventHandledJpaRepository.existsById("dup-event-id")).thenReturn(true);

            // act
            facade.processIssue(payload);

            // assert
            verify(userCouponRepository, never()).save(any());
            verify(couponIssueRequestRepository, never()).findByRequestId(any());
        }

        @DisplayName("수량 제한 쿠폰인데 발급 수량이 초과됐으면, 요청 상태가 FAILED 로 변경된다.")
        @Test
        void marksFailed_whenQuantityExceeded() {
            // arrange
            CouponIssuePayload payload = new CouponIssuePayload("event-1", "req-1", 1L, 2L);
            Coupon coupon = mock(Coupon.class);
            CouponIssueRequest request = mock(CouponIssueRequest.class);

            when(eventHandledJpaRepository.existsById("event-1")).thenReturn(false);
            when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
            when(couponIssueRequestRepository.findByRequestId("req-1")).thenReturn(Optional.of(request));
            when(coupon.isLimited()).thenReturn(true);
            when(coupon.totalQuantity()).thenReturn(100);
            when(userCouponRepository.countByCouponTemplateId(1L)).thenReturn(100L);

            // act
            facade.processIssue(payload);

            // assert
            verify(request).markFailed("수량 초과");
            verify(userCouponRepository, never()).save(any());
            verify(eventHandledJpaRepository).save(any(EventHandled.class));
        }

        @DisplayName("이미 발급받은 유저이면, 요청 상태가 FAILED 로 변경된다.")
        @Test
        void marksFailed_whenAlreadyIssued() {
            // arrange
            CouponIssuePayload payload = new CouponIssuePayload("event-1", "req-1", 1L, 2L);
            Coupon coupon = mock(Coupon.class);
            CouponIssueRequest request = mock(CouponIssueRequest.class);

            when(eventHandledJpaRepository.existsById("event-1")).thenReturn(false);
            when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
            when(couponIssueRequestRepository.findByRequestId("req-1")).thenReturn(Optional.of(request));
            when(coupon.isLimited()).thenReturn(true);
            when(coupon.totalQuantity()).thenReturn(100);
            when(userCouponRepository.countByCouponTemplateId(1L)).thenReturn(50L);
            when(userCouponRepository.existsByUserIdAndCouponTemplateId(2L, 1L)).thenReturn(true);

            // act
            facade.processIssue(payload);

            // assert
            verify(request).markFailed("중복 발급");
            verify(userCouponRepository, never()).save(any());
            verify(eventHandledJpaRepository).save(any(EventHandled.class));
        }

        @DisplayName("정상 발급이면, UserCoupon 이 생성되고 요청 상태가 SUCCESS 로 변경된다.")
        @Test
        void issuesCoupon_whenValid() {
            // arrange
            CouponIssuePayload payload = new CouponIssuePayload("event-1", "req-1", 1L, 2L);
            Coupon coupon = mock(Coupon.class);
            CouponIssueRequest request = mock(CouponIssueRequest.class);

            when(eventHandledJpaRepository.existsById("event-1")).thenReturn(false);
            when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
            when(couponIssueRequestRepository.findByRequestId("req-1")).thenReturn(Optional.of(request));
            when(coupon.isLimited()).thenReturn(true);
            when(coupon.totalQuantity()).thenReturn(100);
            when(userCouponRepository.countByCouponTemplateId(1L)).thenReturn(50L);
            when(userCouponRepository.existsByUserIdAndCouponTemplateId(2L, 1L)).thenReturn(false);

            // act
            facade.processIssue(payload);

            // assert
            verify(userCouponRepository).save(any());
            verify(request).markSuccess();
            verify(eventHandledJpaRepository).save(any(EventHandled.class));
        }

        @DisplayName("수량 제한이 없는 쿠폰이면, 수량 체크 없이 발급된다.")
        @Test
        void issuesCoupon_whenUnlimited() {
            // arrange
            CouponIssuePayload payload = new CouponIssuePayload("event-1", "req-1", 1L, 2L);
            Coupon coupon = mock(Coupon.class);
            CouponIssueRequest request = mock(CouponIssueRequest.class);

            when(eventHandledJpaRepository.existsById("event-1")).thenReturn(false);
            when(couponRepository.findById(1L)).thenReturn(Optional.of(coupon));
            when(couponIssueRequestRepository.findByRequestId("req-1")).thenReturn(Optional.of(request));
            when(coupon.isLimited()).thenReturn(false);
            when(userCouponRepository.existsByUserIdAndCouponTemplateId(2L, 1L)).thenReturn(false);

            // act
            facade.processIssue(payload);

            // assert
            verify(userCouponRepository, never()).countByCouponTemplateId(any());
            verify(userCouponRepository).save(any());
            verify(request).markSuccess();
        }
    }
}

package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponEvent;
import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueStatus;
import com.loopers.domain.coupon.CouponRepository;
import com.loopers.domain.coupon.UserCouponRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CouponFacadeTest {

    UserRepository userRepository = mock(UserRepository.class);
    CouponRepository couponRepository = mock(CouponRepository.class);
    UserCouponRepository userCouponRepository = mock(UserCouponRepository.class);
    CouponIssueRequestRepository couponIssueRequestRepository = mock(CouponIssueRequestRepository.class);
    ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);

    CouponFacade couponFacade = new CouponFacade(
            userRepository,
            couponRepository,
            userCouponRepository,
            couponIssueRequestRepository,
            eventPublisher
    );

    @DisplayName("쿠폰 발급을 요청할 때, ")
    @Nested
    class RequestIssue {

        @DisplayName("존재하지 않는 쿠폰 ID 이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenCouponNotFound() {
            // arrange
            Long couponId = 1L;
            Long userId = 2L;
            when(couponRepository.findById(couponId)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.requestIssue(couponId, userId));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 유저 ID 이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenUserNotFound() {
            // arrange
            Long couponId = 1L;
            Long userId = 2L;
            Coupon coupon = Coupon.of("선착순 쿠폰", "FIXED", 1000, 100, ZonedDateTime.now().plusDays(30));
            when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class,
                    () -> couponFacade.requestIssue(couponId, userId));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("정상 요청이면, PENDING 상태의 요청이 저장되고 이벤트가 발행된다.")
        @Test
        void savesPendingRequest_andPublishesEvent_whenValid() {
            // arrange
            Long couponId = 1L;
            Long userId = 2L;
            Coupon coupon = Coupon.of("선착순 쿠폰", "FIXED", 1000, 100, ZonedDateTime.now().plusDays(30));
            User user = mock(User.class);
            when(user.getId()).thenReturn(userId);
            when(couponRepository.findById(couponId)).thenReturn(Optional.of(coupon));
            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(couponIssueRequestRepository.save(any())).thenReturn(1L);

            // act
            String requestId = couponFacade.requestIssue(couponId, userId);

            // assert
            assertThat(requestId).isNotBlank();

            ArgumentCaptor<CouponIssueRequest> captor = ArgumentCaptor.forClass(CouponIssueRequest.class);
            verify(couponIssueRequestRepository).save(captor.capture());
            assertThat(captor.getValue().status()).isEqualTo(CouponIssueStatus.PENDING);
            assertThat(captor.getValue().couponId()).isEqualTo(couponId);
            assertThat(captor.getValue().userId()).isEqualTo(userId);

            verify(eventPublisher).publishEvent(any(CouponEvent.IssueRequested.class));
        }
    }
}

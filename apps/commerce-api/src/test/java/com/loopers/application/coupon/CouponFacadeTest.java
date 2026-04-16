package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.domain.outbox.OutboxEventPublisher;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserService;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CouponFacadeTest {

    @Mock
    private CouponService couponService;

    @Mock
    private UserService userService;

    @Mock
    private OutboxEventPublisher outboxEventPublisher;

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @Mock
    private IssuedCouponRepository issuedCouponRepository;

    private CouponFacade couponFacade;

    @BeforeEach
    void setUp() {
        couponFacade = new CouponFacade(couponService, userService, outboxEventPublisher,
            couponTemplateRepository, issuedCouponRepository);
    }

    @DisplayName("쿠폰 발급 요청")
    @Nested
    class RequestIssue {

        @DisplayName("유효한 쿠폰으로 발급 요청하면, 발급 요청 정보를 반환한다.")
        @Test
        void returnsCouponIssueInfo_whenCouponIsValid() {
            // Arrange
            String loginId = "user1";
            String rawPassword = "Test1234!";
            User user = new User(loginId, "encoded", "홍길동", "19900101", "test@naver.com");
            CouponIssueRequest request = new CouponIssueRequest(1L, user.getId());

            given(userService.authenticate(loginId, rawPassword)).willReturn(user);
            given(couponService.createRequest(1L, user.getId())).willReturn(request);

            // Act
            CouponIssueInfo result = couponFacade.requestIssue(loginId, rawPassword, 1L);

            // Assert
            assertThat(result).isNotNull();
        }

        @DisplayName("존재하지 않는 쿠폰으로 발급 요청하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            // Arrange
            String loginId = "user1";
            String rawPassword = "Test1234!";
            User user = new User(loginId, "encoded", "홍길동", "19900101", "test@naver.com");

            given(userService.authenticate(loginId, rawPassword)).willReturn(user);
            given(couponService.createRequest(any(), any()))
                .willThrow(new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));

            // Act
            CoreException exception = assertThrows(CoreException.class,
                () -> couponFacade.requestIssue(loginId, rawPassword, 999L));

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 발급 결과 조회")
    @Nested
    class GetIssueResult {

        @DisplayName("인증된 유저가 발급 요청 결과를 조회하면 결과를 반환한다.")
        @Test
        void returnsIssueResult_whenAuthenticated() {
            // Arrange
            String loginId = "user1";
            String rawPassword = "Test1234!";
            User user = new User(loginId, "encoded", "홍길동", "19900101", "test@naver.com");
            CouponIssueRequest request = new CouponIssueRequest(1L, user.getId());

            given(userService.authenticate(loginId, rawPassword)).willReturn(user);
            given(couponService.getRequest(1L)).willReturn(request);

            // Act
            CouponIssueInfo result = couponFacade.getIssueResult(loginId, rawPassword, 1L);

            // Assert
            assertThat(result).isNotNull();
        }
    }
}

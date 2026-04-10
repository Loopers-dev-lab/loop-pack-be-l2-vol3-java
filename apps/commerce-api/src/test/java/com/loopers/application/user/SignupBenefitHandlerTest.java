package com.loopers.application.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.domain.coupon.CouponModel;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.dto.CouponCommand;
import com.loopers.domain.user.UserService;
import com.loopers.domain.user.event.UserSignedUpEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignupBenefitHandlerTest {

    @Mock UserService userService;
    @Mock CouponService couponService;
    SignupBenefitHandler handler;

    UserSignedUpEvent event;

    @BeforeEach
    void setUp() {
        handler = new SignupBenefitHandler(userService, couponService);
        event = new UserSignedUpEvent(1L, "홍길동", "test@example.com");
    }

    @DisplayName("회원가입 이벤트를 수신할 때, ")
    @Nested
    class Handle {

        @DisplayName("웰컴 포인트 1000원을 지급한다.")
        @Test
        void handle_addsWelcomePoint() {
            // act
            handler.handle(event);

            // assert
            verify(userService).addPoint(1L, 1000L);
        }

        @DisplayName("웰컴 쿠폰을 생성하고 발급한다.")
        @Test
        void handle_createsAndIssuesWelcomeCoupon() {
            // arrange
            CouponModel stubCoupon = mock(CouponModel.class);
            when(stubCoupon.getId()).thenReturn(100L);
            when(couponService.register(any(CouponCommand.Create.class))).thenReturn(stubCoupon);

            // act
            handler.handle(event);

            // assert
            verify(couponService).register(any(CouponCommand.Create.class));
            verify(couponService).issue(100L, 1L);
        }

        @DisplayName("예외 발생 시 로그만 남기고 전파하지 않는다.")
        @Test
        void handle_whenExceptionOccurs_doesNotPropagate() {
            // arrange
            doThrow(new RuntimeException("포인트 지급 실패"))
                    .when(userService).addPoint(anyLong(), anyLong());

            // act & assert
            assertThatCode(() -> handler.handle(event)).doesNotThrowAnyException();
        }
    }
}

package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.ZonedDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class UserCouponServiceTest {

    @Mock
    private CouponService couponService;

    @Mock
    private UserCouponRepository userCouponRepository;

    private UserCouponService userCouponService;

    @BeforeEach
    void setUp() {
        userCouponService = new UserCouponService(couponService, userCouponRepository);
    }

    @DisplayName("쿠폰 발급 시, ")
    @Nested
    class Issue {

        @DisplayName("이미 발급된 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            CouponModel coupon = new CouponModel(
                "신규가입",
                CouponType.FIXED,
                1000L,
                null,
                ZonedDateTime.now().plusDays(1)
            );
            given(couponService.getCoupon(1L)).willReturn(coupon);
            given(userCouponRepository.existsByUserIdAndCouponId(10L, 1L)).willReturn(true);

            CoreException result = assertThrows(CoreException.class, () -> userCouponService.issue(10L, 1L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("동시 발급으로 DB 유니크 충돌이 발생하면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenUniqueConstraintViolationOccurs() {
            CouponModel coupon = new CouponModel(
                "신규가입",
                CouponType.FIXED,
                1000L,
                null,
                ZonedDateTime.now().plusDays(1)
            );
            given(couponService.getCoupon(1L)).willReturn(coupon);
            given(userCouponRepository.existsByUserIdAndCouponId(10L, 1L)).willReturn(false);
            given(userCouponRepository.save(any(UserCouponModel.class)))
                .willThrow(new DataIntegrityViolationException("duplicate key"));

            CoreException result = assertThrows(CoreException.class, () -> userCouponService.issue(10L, 1L));

            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("쿠폰 사용 준비 시, ")
    @Nested
    class GetAvailableForUse {

        @DisplayName("다른 사용자 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotBelongToUser() {
            given(userCouponRepository.findByIdAndUserIdForUpdate(100L, 10L)).willReturn(Optional.empty());

            CoreException result = assertThrows(CoreException.class, () ->
                userCouponService.getAvailableUserCouponForUse(10L, 100L)
            );

            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}

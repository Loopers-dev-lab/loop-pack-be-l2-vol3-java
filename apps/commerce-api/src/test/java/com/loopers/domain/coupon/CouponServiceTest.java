package com.loopers.domain.coupon;

import com.loopers.support.enums.DiscountType;
import com.loopers.support.enums.UserCouponStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService 단위 테스트")
class CouponServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long COUPON_ID = 10L;
    private static final Long USER_COUPON_ID = 100L;

    @Mock
    CouponRepository couponRepository;

    @Mock
    UserCouponRepository userCouponRepository;

    @InjectMocks
    CouponService couponService;

    private CouponModel createValidCoupon() {
        return CouponModel.create("테스트쿠폰", DiscountType.FIXED, BigDecimal.valueOf(5000), null,
                LocalDateTime.now().plusDays(30));
    }

    @Nested
    @DisplayName("issueCoupon - 쿠폰 발급")
    class IssueCouponTests {

        @Test
        @DisplayName("정상 발급 시 UserCouponModel이 저장된다")
        void issueCoupon_NewIssuance_ShouldSave() {
            CouponModel coupon = createValidCoupon();
            when(couponRepository.findByIdWithLock(COUPON_ID)).thenReturn(Optional.of(coupon));
            when(userCouponRepository.existsByUserIdAndCouponId(USER_ID, COUPON_ID)).thenReturn(false);
            UserCouponModel saved = UserCouponModel.create(USER_ID, COUPON_ID);
            when(userCouponRepository.save(any())).thenReturn(saved);

            UserCouponModel result = couponService.issueCoupon(USER_ID, COUPON_ID);

            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(UserCouponStatus.AVAILABLE);
            verify(userCouponRepository).save(any(UserCouponModel.class));
        }

        @Test
        @DisplayName("쿠폰이 없으면 COUPON_NOT_FOUND 예외가 발생한다")
        void issueCoupon_CouponNotFound_ShouldThrowCouponNotFound() {
            when(couponRepository.findByIdWithLock(COUPON_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> couponService.issueCoupon(USER_ID, COUPON_ID))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.COUPON_NOT_FOUND));
            verify(userCouponRepository, never()).save(any());
        }

        @Test
        @DisplayName("이미 발급된 쿠폰이면 COUPON_ALREADY_ISSUED 예외가 발생한다")
        void issueCoupon_AlreadyIssued_ShouldThrowCouponAlreadyIssued() {
            CouponModel coupon = createValidCoupon();
            when(couponRepository.findByIdWithLock(COUPON_ID)).thenReturn(Optional.of(coupon));
            when(userCouponRepository.existsByUserIdAndCouponId(USER_ID, COUPON_ID)).thenReturn(true);

            assertThatThrownBy(() -> couponService.issueCoupon(USER_ID, COUPON_ID))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.COUPON_ALREADY_ISSUED));
            verify(userCouponRepository, never()).save(any());
        }

        @Test
        @DisplayName("만료된 쿠폰이면 COUPON_NOT_APPLICABLE 예외가 발생한다")
        void issueCoupon_ExpiredCoupon_ShouldThrowCouponNotApplicable() {
            CouponModel expiredCoupon = CouponModel.create("만료쿠폰", DiscountType.FIXED,
                    BigDecimal.valueOf(1000), null, LocalDateTime.now().minusDays(1));
            when(couponRepository.findByIdWithLock(COUPON_ID)).thenReturn(Optional.of(expiredCoupon));

            assertThatThrownBy(() -> couponService.issueCoupon(USER_ID, COUPON_ID))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.COUPON_NOT_APPLICABLE));
            verify(userCouponRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("validateAndGetUserCoupon - 발급 쿠폰 조회 및 검증")
    class ValidateAndGetUserCouponTests {

        @Test
        @DisplayName("AVAILABLE 상태의 본인 쿠폰이면 반환한다")
        void validateAndGetUserCoupon_WhenAvailable_ShouldReturn() {
            UserCouponModel userCoupon = UserCouponModel.create(USER_ID, COUPON_ID);
            when(userCouponRepository.findByIdWithLock(USER_COUPON_ID))
                    .thenReturn(Optional.of(userCoupon));

            UserCouponModel result = couponService.validateAndGetUserCoupon(USER_ID, USER_COUPON_ID);

            assertThat(result).isNotNull();
            assertThat(result.isAvailable()).isTrue();
        }

        @Test
        @DisplayName("다른 사용자 소유 쿠폰이면 USER_COUPON_NOT_FOUND 예외가 발생한다")
        void validateAndGetUserCoupon_WhenNotOwned_ShouldThrowUserCouponNotFound() {
            UserCouponModel otherUserCoupon = UserCouponModel.create(999L, COUPON_ID);
            when(userCouponRepository.findByIdWithLock(USER_COUPON_ID))
                    .thenReturn(Optional.of(otherUserCoupon));

            assertThatThrownBy(() -> couponService.validateAndGetUserCoupon(USER_ID, USER_COUPON_ID))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.USER_COUPON_NOT_FOUND));
        }

        @Test
        @DisplayName("USED 상태의 쿠폰이면 COUPON_NOT_AVAILABLE 예외가 발생한다")
        void validateAndGetUserCoupon_WhenUsed_ShouldThrowCouponNotAvailable() {
            UserCouponModel userCoupon = UserCouponModel.create(USER_ID, COUPON_ID);
            userCoupon.markAsUsed(999L);
            when(userCouponRepository.findByIdWithLock(USER_COUPON_ID))
                    .thenReturn(Optional.of(userCoupon));

            assertThatThrownBy(() -> couponService.validateAndGetUserCoupon(USER_ID, USER_COUPON_ID))
                    .isInstanceOf(CoreException.class)
                    .satisfies(ex -> assertThat(((CoreException) ex).getErrorType())
                            .isEqualTo(ErrorType.COUPON_NOT_AVAILABLE));
        }
    }
}

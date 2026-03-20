package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.loopers.domain.coupon.discount.CouponDiscount;
import com.loopers.domain.shared.Money;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class OwnedCouponServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private OwnedCouponService ownedCouponService;

    @Autowired
    private CouponService couponService;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private OwnedCouponRepository ownedCouponRepository;

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class Issue {

        @DisplayName("유효한 쿠폰을 발급하면, 보유 쿠폰이 DB에 저장된다.")
        @Test
        void savesOwnedCouponToDatabase_whenValidCouponProvided() {
            // arrange
            var coupon = couponService.create(new CouponTerms("발급 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            var userId = 1L;

            // act
            var result = ownedCouponService.issue(coupon.getId(), userId);

            // assert
            assertAll(
                    () -> assertThat(result.getCoupon().getId()).isEqualTo(coupon.getId()),
                    () -> assertThat(result.getUserId()).isEqualTo(userId),
                    () -> assertThat(result.getStatus()).isEqualTo("AVAILABLE")
            );
        }

        @DisplayName("존재하지 않는 쿠폰을 발급하면, COUPON_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenCouponNotFound() {
            // act & assert
            assertThatThrownBy(() -> ownedCouponService.issue(999L, 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.COUPON_NOT_FOUND));
        }

        @DisplayName("삭제된 쿠폰을 발급하면, COUPON_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenCouponIsDeleted() {
            // arrange
            var coupon = couponService.create(new CouponTerms("삭제 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            couponService.delete(coupon.getId());

            // act & assert
            assertThatThrownBy(() -> ownedCouponService.issue(coupon.getId(), 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.COUPON_NOT_FOUND));
        }

        @DisplayName("만료된 쿠폰을 발급하면, EXPIRED_COUPON 예외가 발생한다.")
        @Test
        void throwsException_whenCouponIsExpired() {
            // arrange
            var coupon = couponService.create(new CouponTerms("만료 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            ReflectionTestUtils.setField(coupon, "expiredAt", ZonedDateTime.now().minusDays(1));
            couponRepository.save(coupon);

            // act & assert
            assertThatThrownBy(() -> ownedCouponService.issue(coupon.getId(), 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.EXPIRED_COUPON));
        }

        @DisplayName("이미 발급받은 쿠폰을 중복 발급하면, DUPLICATE_COUPON_ISSUE 예외가 발생한다.")
        @Test
        void throwsException_whenDuplicateIssue() {
            // arrange
            var coupon = couponService.create(new CouponTerms("중복 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            ownedCouponService.issue(coupon.getId(), 1L);

            // act & assert
            assertThatThrownBy(() -> ownedCouponService.issue(coupon.getId(), 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.ALREADY_COUPON_ISSUED));
        }
    }

    @DisplayName("쿠폰을 적용할 때,")
    @Nested
    class ApplyDiscount {

        @DisplayName("유효한 쿠폰이면, 할인 금액을 계산하고 USED 상태로 변경된다.")
        @Test
        void calculatesDiscountAndMarksUsed() {
            // arrange
            var coupon = couponService.create(new CouponTerms("5000원 할인", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            var ownedCoupon = ownedCouponService.issue(coupon.getId(), 1L);

            // act
            var result = ownedCouponService.applyDiscount(ownedCoupon.getId(), 1L, Money.wons(20000L));

            // assert
            var saved = ownedCouponRepository.findByIdWithCoupon(ownedCoupon.getId()).orElseThrow();
            assertAll(
                    () -> assertThat(result.discountAmount()).isEqualTo(Money.wons(5000L)),
                    () -> assertThat(result.ownedCouponId()).isEqualTo(ownedCoupon.getId()),
                    () -> assertThat(saved.getStatus()).isEqualTo("USED")
            );
        }

        @DisplayName("쿠폰 ID가 null이면, CouponDiscount.NONE을 반환한다.")
        @Test
        void returnsNone_whenNull() {
            // act
            CouponDiscount result = ownedCouponService.applyDiscount(null, 1L, Money.wons(20000L));

            // assert
            assertAll(
                    () -> assertThat(result.discountAmount()).isEqualTo(Money.ZERO),
                    () -> assertThat(result.ownedCouponId()).isNull()
            );
        }
    }

    @DisplayName("쿠폰을 복원할 때,")
    @Nested
    class Restore {

        @DisplayName("사용된 쿠폰이면, AVAILABLE 상태로 변경된다.")
        @Test
        void changesStatusToAvailable() {
            // arrange
            var coupon = couponService.create(new CouponTerms("복원 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30)));
            var ownedCoupon = ownedCouponService.issue(coupon.getId(), 1L);
            ownedCouponService.applyDiscount(ownedCoupon.getId(), 1L, Money.wons(20000L));

            // act
            ownedCouponService.restore(ownedCoupon.getId());

            // assert
            var saved = ownedCouponRepository.findByIdWithCoupon(ownedCoupon.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo("AVAILABLE");
        }

        @DisplayName("존재하지 않는 보유 쿠폰이면, OWNED_COUPON_NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsException_whenNotFound() {
            assertThatThrownBy(() -> ownedCouponService.restore(999L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.OWNED_COUPON_NOT_FOUND));
        }
    }

}

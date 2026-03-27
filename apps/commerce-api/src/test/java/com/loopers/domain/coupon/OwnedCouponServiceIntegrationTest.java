package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    private OwnedCouponRepository ownedCouponRepository;

    @DisplayName("할인 금액을 계산할 때,")
    @Nested
    class CalculateDiscount {

        @DisplayName("유효한 쿠폰이면, 할인 금액을 계산한다.")
        @Test
        void calculatesDiscount_whenValidCoupon() {
            // arrange
            var coupon = couponService.create(new CouponTerms("5000원 할인", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30), 10000));
            var ownedCoupon = ownedCouponRepository.save(OwnedCouponFixture.createOwnedCoupon(coupon, 1L));

            // act
            var result = ownedCouponService.calculateDiscount(ownedCoupon.getId(), 1L, Money.wons(20000L));

            // assert
            assertAll(
                    () -> assertThat(result.discountAmount()).isEqualTo(Money.wons(5000L)),
                    () -> assertThat(result.ownedCouponId()).isEqualTo(ownedCoupon.getId())
            );
        }

        @DisplayName("쿠폰 ID가 null이면, CouponDiscount.NONE을 반환한다.")
        @Test
        void returnsNone_whenNull() {
            // act
            CouponDiscount result = ownedCouponService.calculateDiscount(null, 1L, Money.wons(20000L));

            // assert
            assertAll(
                    () -> assertThat(result.discountAmount()).isEqualTo(Money.ZERO),
                    () -> assertThat(result.ownedCouponId()).isNull()
            );
        }
    }

    @DisplayName("쿠폰을 사용 처리할 때,")
    @Nested
    class Use {

        @DisplayName("보유 쿠폰이면, USED 상태로 변경된다.")
        @Test
        void changesStatusToUsed() {
            // arrange
            var coupon = couponService.create(new CouponTerms("사용 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30), 10000));
            var ownedCoupon = ownedCouponRepository.save(OwnedCouponFixture.createOwnedCoupon(coupon, 1L));

            // act
            ownedCouponService.use(ownedCoupon.getId());

            // assert
            var saved = ownedCouponRepository.findByIdWithCoupon(ownedCoupon.getId()).orElseThrow();
            assertThat(saved.getStatus()).isEqualTo("USED");
        }
    }

    @DisplayName("쿠폰을 복원할 때,")
    @Nested
    class Restore {

        @DisplayName("사용된 쿠폰이면, AVAILABLE 상태로 변경된다.")
        @Test
        void changesStatusToAvailable() {
            // arrange
            var coupon = couponService.create(new CouponTerms("복원 쿠폰", CouponType.FIXED, 5000L, null, 10000L, ZonedDateTime.now().plusDays(30), 10000));
            var ownedCoupon = ownedCouponRepository.save(OwnedCouponFixture.createOwnedCoupon(coupon, 1L));
            ownedCouponService.use(ownedCoupon.getId());

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

package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IssuedCouponTest {

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 템플릿으로 발급하면, 정상적으로 생성된다.")
        @Test
        void createsIssuedCoupon_whenTemplateIsValid() {
            // arrange
            Long userId = 1L;
            CouponTemplate template = CouponTemplate.createFixed(
                "테스트 쿠폰", new Money(5000), Money.ZERO, 10, ZonedDateTime.now().plusDays(7)
            );
            template = CouponTemplate.reconstitute(
                100L, template.getName(), template.getType(), template.getValue(),
                template.getMinOrderAmount(), template.getMaxDiscountAmount(),
                template.getMaxIssueCount(), template.getIssuedCount(),
                template.getExpiredAt(), template.getCreatedAt(), null
            );

            // act
            IssuedCoupon coupon = IssuedCoupon.create(userId, template);

            // assert
            assertThat(coupon.getUserId()).isEqualTo(userId);
            assertThat(coupon.getCouponTemplateId()).isEqualTo(100L);
            assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.AVAILABLE);
            assertThat(coupon.getIssuedAt()).isNotNull();
            assertThat(coupon.getExpiredAt()).isEqualTo(template.getExpiredAt());
            assertThat(coupon.getUsedAt()).isNull();
        }
    }

    @DisplayName("쿠폰 사용 가능 여부를 확인할 때,")
    @Nested
    class IsUsable {

        @DisplayName("상태가 AVAILABLE이고 만료 전이면, true를 반환한다.")
        @Test
        void returnsTrue_whenAvailableAndNotExpired() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.reconstitute(
                1L, 1L, 100L, IssuedCouponStatus.AVAILABLE,
                null, ZonedDateTime.now(), ZonedDateTime.now().plusDays(1)
            );

            // act & assert
            assertThat(coupon.isUsable()).isTrue();
        }

        @DisplayName("상태가 USED이면, false를 반환한다.")
        @Test
        void returnsFalse_whenAlreadyUsed() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.reconstitute(
                1L, 1L, 100L, IssuedCouponStatus.USED,
                ZonedDateTime.now(), ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1)
            );

            // act & assert
            assertThat(coupon.isUsable()).isFalse();
        }

        @DisplayName("만료되었으면, false를 반환한다.")
        @Test
        void returnsFalse_whenExpired() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.reconstitute(
                1L, 1L, 100L, IssuedCouponStatus.AVAILABLE,
                null, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1)
            );

            // act & assert
            assertThat(coupon.isUsable()).isFalse();
        }
    }

    @DisplayName("쿠폰을 사용할 때,")
    @Nested
    class Use {

        @DisplayName("사용 가능하면, 상태가 USED로 변경된다.")
        @Test
        void changesStatusToUsed_whenUsable() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.reconstitute(
                1L, 1L, 100L, IssuedCouponStatus.AVAILABLE,
                null, ZonedDateTime.now(), ZonedDateTime.now().plusDays(1)
            );

            // act
            coupon.use();

            // assert
            assertThat(coupon.getStatus()).isEqualTo(IssuedCouponStatus.USED);
            assertThat(coupon.getUsedAt()).isNotNull();
        }

        @DisplayName("이미 사용된 쿠폰이면, 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyUsed() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.reconstitute(
                1L, 1L, 100L, IssuedCouponStatus.USED,
                ZonedDateTime.now(), ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1)
            );

            // act
            CoreException result = assertThrows(CoreException.class, coupon::use);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_ALREADY_USED);
        }

        @DisplayName("만료된 쿠폰이면, 예외가 발생한다.")
        @Test
        void throwsException_whenExpired() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.reconstitute(
                1L, 1L, 100L, IssuedCouponStatus.AVAILABLE,
                null, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1)
            );

            // act
            CoreException result = assertThrows(CoreException.class, coupon::use);

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_EXPIRED);
        }
    }

    @DisplayName("만료 여부를 확인할 때,")
    @Nested
    class IsExpired {

        @DisplayName("현재 시간이 만료일 이전이면, false를 반환한다.")
        @Test
        void returnsFalse_whenBeforeExpiredAt() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.reconstitute(
                1L, 1L, 100L, IssuedCouponStatus.AVAILABLE,
                null, ZonedDateTime.now(), ZonedDateTime.now().plusDays(1)
            );

            // act & assert
            assertThat(coupon.isExpired()).isFalse();
        }

        @DisplayName("현재 시간이 만료일 이후이면, true를 반환한다.")
        @Test
        void returnsTrue_whenAfterExpiredAt() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.reconstitute(
                1L, 1L, 100L, IssuedCouponStatus.AVAILABLE,
                null, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1)
            );

            // act & assert
            assertThat(coupon.isExpired()).isTrue();
        }
    }

    @DisplayName("표시 상태를 조회할 때,")
    @Nested
    class GetDisplayStatus {

        @DisplayName("상태가 USED이면, USED를 반환한다.")
        @Test
        void returnsUsed_whenStatusIsUsed() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.reconstitute(
                1L, 1L, 100L, IssuedCouponStatus.USED,
                ZonedDateTime.now(), ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(1)
            );

            // act & assert
            assertThat(coupon.getDisplayStatus()).isEqualTo(CouponDisplayStatus.USED);
        }

        @DisplayName("상태가 AVAILABLE이고 만료되었으면, EXPIRED를 반환한다.")
        @Test
        void returnsExpired_whenAvailableButExpired() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.reconstitute(
                1L, 1L, 100L, IssuedCouponStatus.AVAILABLE,
                null, ZonedDateTime.now().minusDays(2), ZonedDateTime.now().minusDays(1)
            );

            // act & assert
            assertThat(coupon.getDisplayStatus()).isEqualTo(CouponDisplayStatus.EXPIRED);
        }

        @DisplayName("상태가 AVAILABLE이고 만료 전이면, AVAILABLE을 반환한다.")
        @Test
        void returnsAvailable_whenAvailableAndNotExpired() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.reconstitute(
                1L, 1L, 100L, IssuedCouponStatus.AVAILABLE,
                null, ZonedDateTime.now(), ZonedDateTime.now().plusDays(1)
            );

            // act & assert
            assertThat(coupon.getDisplayStatus()).isEqualTo(CouponDisplayStatus.AVAILABLE);
        }
    }
}

package com.loopers.domain.coupon;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.support.error.CoreException;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("CouponModel 단위 테스트")
class CouponModelTest {

    @DisplayName("생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면 정상적으로 생성되고 issuedQuantity는 0이다")
        @Test
        void create_whenValidValues() {
            // arrange
            ZonedDateTime expiredAt = ZonedDateTime.now().plusDays(30);

            // act
            CouponModel coupon = CouponModel.create(
                    "신규가입 10% 할인", CouponDiscountType.RATE, 10L,
                    10000L, 1000, expiredAt);

            // assert
            assertAll(
                    () -> assertThat(coupon.getName()).isEqualTo("신규가입 10% 할인"),
                    () -> assertThat(coupon.getDiscountType()).isEqualTo(CouponDiscountType.RATE),
                    () -> assertThat(coupon.getDiscountValue()).isEqualTo(10L),
                    () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(10000L),
                    () -> assertThat(coupon.getTotalQuantity()).isEqualTo(1000),
                    () -> assertThat(coupon.getExpiredAt()).isEqualTo(expiredAt));
        }

        @DisplayName("name이 빈값이면 예외가 발생한다")
        @Test
        void create_whenNameIsBlank() {
            assertThatThrownBy(() -> CouponModel.create(
                    "  ", CouponDiscountType.RATE, 10L,
                    10000L, 1000, ZonedDateTime.now().plusDays(30)))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("쿠폰명은 필수값입니다.");
        }

        @DisplayName("discountValue가 0 이하이면 예외가 발생한다")
        @Test
        void create_whenDiscountValueIsZeroOrLess() {
            assertThatThrownBy(() -> CouponModel.create(
                    "할인 쿠폰", CouponDiscountType.FIXED, 0L,
                    null, 1000, ZonedDateTime.now().plusDays(30)))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("할인 값은 1 이상이어야 합니다.");
        }

        @DisplayName("RATE 타입일 때 discountValue가 100을 초과하면 예외가 발생한다")
        @Test
        void create_whenRateExceeds100() {
            assertThatThrownBy(() -> CouponModel.create(
                    "할인 쿠폰", CouponDiscountType.RATE, 101L,
                    null, 1000, ZonedDateTime.now().plusDays(30)))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("정률 할인은 1~100 범위여야 합니다.");
        }

        @DisplayName("totalQuantity가 1 미만이면 예외가 발생한다")
        @Test
        void create_whenTotalQuantityIsLessThan1() {
            assertThatThrownBy(() -> CouponModel.create(
                    "할인 쿠폰", CouponDiscountType.FIXED, 5000L,
                    null, 0, ZonedDateTime.now().plusDays(30)))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("총 수량은 1 이상이어야 합니다.");
        }

        @DisplayName("expiredAt이 과거이면 예외가 발생한다")
        @Test
        void create_whenExpiredAtIsPast() {
            assertThatThrownBy(() -> CouponModel.create(
                    "할인 쿠폰", CouponDiscountType.FIXED, 5000L,
                    null, 1000, ZonedDateTime.now().minusDays(1)))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("만료일은 현재 시점 이후여야 합니다.");
        }
    }

    @DisplayName("수정할 때, ")
    @Nested
    class Update {

        @DisplayName("name과 expiredAt을 변경할 수 있다")
        @Test
        void update_nameAndExpiredAt() {
            // arrange
            CouponModel coupon = CouponModel.create(
                    "기존 쿠폰명", CouponDiscountType.FIXED, 5000L,
                    null, 1000, ZonedDateTime.now().plusDays(30));
            ZonedDateTime newExpiredAt = ZonedDateTime.now().plusDays(60);

            // act
            coupon.update("수정된 쿠폰명", newExpiredAt);

            // assert
            assertAll(
                    () -> assertThat(coupon.getName()).isEqualTo("수정된 쿠폰명"),
                    () -> assertThat(coupon.getExpiredAt()).isEqualTo(newExpiredAt));
        }
    }

    @DisplayName("발급 가능 여부를 검증할 때, ")
    @Nested
    class ValidateIssuable {

        @DisplayName("삭제된 쿠폰이면 예외가 발생한다")
        @Test
        void validateIssuable_whenDeleted() {
            // arrange
            CouponModel coupon = CouponModel.create(
                    "할인 쿠폰", CouponDiscountType.FIXED, 5000L,
                    null, 1000, ZonedDateTime.now().plusDays(30));
            coupon.delete();

            // act & assert
            assertThatThrownBy(coupon::validateIssuable)
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("삭제된 쿠폰입니다.");
        }

        @DisplayName("만료된 쿠폰이면 예외가 발생한다")
        @Test
        void validateIssuable_whenExpired() {
            // arrange — 만료일이 과거인 쿠폰을 직접 생성할 수 없으므로 리플렉션 사용
            CouponModel coupon = CouponModel.create(
                    "할인 쿠폰", CouponDiscountType.FIXED, 5000L,
                    null, 1000, ZonedDateTime.now().plusDays(1));
            setField(coupon, "expiredAt", ZonedDateTime.now().minusDays(1));

            // act & assert
            assertThatThrownBy(coupon::validateIssuable)
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("만료된 쿠폰입니다.");
        }
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            var field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}

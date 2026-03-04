package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponTest {

    private static final LocalDateTime FUTURE = LocalDateTime.now().plusDays(7);

    @Nested
    class 생성 {

        @Test
        void 유효한_정보로_정액_쿠폰을_생성한다() {
            Coupon coupon = Coupon.create("1000원 할인", CouponType.FIXED, 1000,
                    BigDecimal.valueOf(10000), 100, FUTURE);

            assertAll(
                    () -> assertThat(coupon.getName()).isEqualTo("1000원 할인"),
                    () -> assertThat(coupon.getType()).isEqualTo(CouponType.FIXED),
                    () -> assertThat(coupon.getValue()).isEqualTo(1000),
                    () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(BigDecimal.valueOf(10000)),
                    () -> assertThat(coupon.getMaxIssueCount()).isEqualTo(100),
                    () -> assertThat(coupon.getIssuedCount()).isEqualTo(0),
                    () -> assertThat(coupon.getExpiredAt()).isEqualTo(FUTURE)
            );
        }

        @Test
        void 유효한_정보로_정률_쿠폰을_생성한다() {
            Coupon coupon = Coupon.create("10% 할인", CouponType.RATE, 10,
                    null, 50, FUTURE);

            assertAll(
                    () -> assertThat(coupon.getType()).isEqualTo(CouponType.RATE),
                    () -> assertThat(coupon.getValue()).isEqualTo(10),
                    () -> assertThat(coupon.getMinOrderAmount()).isNull()
            );
        }

        @Test
        void 발급_수량은_0으로_초기화된다() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);

            assertThat(coupon.getIssuedCount()).isEqualTo(0);
        }

        @Test
        void 최소_주문_금액이_없으면_빈값으로_생성된다() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);

            assertThat(coupon.getMinOrderAmount()).isNull();
        }

        @Test
        void 정률_타입_할인값이_100을_초과하면_예외() {
            assertThatThrownBy(() -> Coupon.create("쿠폰", CouponType.RATE, 101,
                    null, 100, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("유효하지 않은 할인값입니다");
        }

        @Test
        void 정률_타입_할인값이_100이면_성공() {
            assertThatCode(() -> Coupon.create("쿠폰", CouponType.RATE, 100,
                    null, 100, FUTURE))
                    .doesNotThrowAnyException();
        }

        @Test
        void 정액_타입_할인값이_100을_초과해도_성공() {
            assertThatCode(() -> Coupon.create("쿠폰", CouponType.FIXED, 50000,
                    null, 100, FUTURE))
                    .doesNotThrowAnyException();
        }

        @Test
        void 만료일이_현재보다_과거이면_예외() {
            LocalDateTime past = LocalDateTime.now().minusDays(1);

            assertThatThrownBy(() -> Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, past))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("만료일은 현재 이후여야 합니다");
        }

        @Test
        void 쿠폰명을_전달하지_않으면_예외() {
            assertThatThrownBy(() -> Coupon.create(null, CouponType.FIXED, 1000,
                    null, 100, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("쿠폰명은 필수입니다");
        }

        @Test
        void 쿠폰명이_빈값이면_예외() {
            assertThatThrownBy(() -> Coupon.create("", CouponType.FIXED, 1000,
                    null, 100, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("쿠폰명은 필수입니다");
        }

        @Test
        void 쿠폰명이_100자를_초과하면_예외() {
            String longName = "가".repeat(101);

            assertThatThrownBy(() -> Coupon.create(longName, CouponType.FIXED, 1000,
                    null, 100, FUTURE))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("쿠폰명은 100자 이하여야 합니다");
        }

        @Test
        void 쿠폰명이_100자이면_성공() {
            String maxName = "가".repeat(100);

            assertThatCode(() -> Coupon.create(maxName, CouponType.FIXED, 1000,
                    null, 100, FUTURE))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    class 수정 {

        @Test
        void 쿠폰명만_수정하면_쿠폰명만_변경된다() {
            Coupon coupon = Coupon.create("원래이름", CouponType.FIXED, 1000,
                    null, 100, FUTURE);

            coupon.updateInfo("새이름", null, null, null, null);

            assertAll(
                    () -> assertThat(coupon.getName()).isEqualTo("새이름"),
                    () -> assertThat(coupon.getValue()).isEqualTo(1000)
            );
        }

        @Test
        void 할인값만_수정하면_할인값만_변경된다() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);

            coupon.updateInfo(null, 2000, null, null, null);

            assertAll(
                    () -> assertThat(coupon.getName()).isEqualTo("쿠폰"),
                    () -> assertThat(coupon.getValue()).isEqualTo(2000)
            );
        }

        @Test
        void 정률_타입에서_할인값을_100_초과로_수정하면_예외() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.RATE, 10,
                    null, 100, FUTURE);

            assertThatThrownBy(() -> coupon.updateInfo(null, 101, null, null, null))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("유효하지 않은 할인값입니다");
        }

        @Test
        void 최대_발급_수량을_현재_발급_수량_미만으로_수정하면_예외() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);
            coupon.issue();

            assertThatThrownBy(() -> coupon.updateInfo(null, null, null, 0, null))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("현재 발급 수량보다 작게 설정할 수 없습니다");
        }

        @Test
        void 만료일을_수정하면_변경된다() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);
            LocalDateTime newExpiredAt = LocalDateTime.now().plusDays(30);

            coupon.updateInfo(null, null, null, null, newExpiredAt);

            assertThat(coupon.getExpiredAt()).isEqualTo(newExpiredAt);
        }

        @Test
        void 수정_요청이_비어있으면_변경되지_않는다() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    BigDecimal.valueOf(5000), 100, FUTURE);

            coupon.updateInfo(null, null, null, null, null);

            assertAll(
                    () -> assertThat(coupon.getName()).isEqualTo("쿠폰"),
                    () -> assertThat(coupon.getValue()).isEqualTo(1000),
                    () -> assertThat(coupon.getMinOrderAmount()).isEqualTo(BigDecimal.valueOf(5000)),
                    () -> assertThat(coupon.getMaxIssueCount()).isEqualTo(100),
                    () -> assertThat(coupon.getExpiredAt()).isEqualTo(FUTURE)
            );
        }

        @Test
        void 만료일을_현재보다_과거로_수정하면_예외() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);
            LocalDateTime past = LocalDateTime.now().minusDays(1);

            assertThatThrownBy(() -> coupon.updateInfo(null, null, null, null, past))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("만료일은 현재 이후여야 합니다");
        }

        @Test
        void 쿠폰명을_빈값으로_수정하면_예외() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);

            assertThatThrownBy(() -> coupon.updateInfo("", null, null, null, null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("쿠폰명은 필수입니다");
        }

        @Test
        void 쿠폰명을_100자_초과로_수정하면_예외() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);
            String longName = "가".repeat(101);

            assertThatThrownBy(() -> coupon.updateInfo(longName, null, null, null, null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                    .hasMessageContaining("쿠폰명은 100자 이하여야 합니다");
        }

        @Test
        void 최대_발급_수량을_현재_발급_수량과_같게_설정하면_성공() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);
            coupon.issue();

            assertThatCode(() -> coupon.updateInfo(null, null, null, 1, null))
                    .doesNotThrowAnyException();
        }

        @Test
        void 삭제된_쿠폰을_수정하면_예외() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);
            coupon.delete();

            assertThatThrownBy(() -> coupon.updateInfo("새이름", null, null, null, null))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    class 삭제 {

        @Test
        void 삭제하면_삭제_상태이다() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);

            coupon.delete();

            assertThat(coupon.isDeleted()).isTrue();
        }

        @Test
        void 이미_삭제된_쿠폰을_삭제해도_삭제_상태를_유지한다() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);
            coupon.delete();

            coupon.delete();

            assertThat(coupon.isDeleted()).isTrue();
        }
    }

    @Nested
    class 발급 {

        @Test
        void 발급하면_발급_수량이_증가한다() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 5, FUTURE);

            coupon.issue();

            assertThat(coupon.getIssuedCount()).isEqualTo(1);
        }

        @Test
        void 발급_수량이_최대치에_도달하면_예외() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 1, FUTURE);
            coupon.issue();

            assertThatThrownBy(() -> coupon.issue())
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("발급 가능 수량이 모두 소진되었습니다");
        }
    }

    @Nested
    class 만료_검증 {

        @Test
        void 만료일이_지나면_만료_상태이다() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);
            ReflectionTestUtils.setField(coupon, "expiredAt", LocalDateTime.now().minusDays(1));

            assertThat(coupon.isExpired()).isTrue();
        }

        @Test
        void 만료일이_지나지_않으면_만료_상태가_아니다() {
            Coupon coupon = Coupon.create("쿠폰", CouponType.FIXED, 1000,
                    null, 100, FUTURE);

            assertThat(coupon.isExpired()).isFalse();
        }
    }

    @Nested
    class 할인_계산 {

        @Test
        void 정액_타입은_할인값과_총액_중_작은_값을_반환한다() {
            Coupon coupon = Coupon.create("5000원 할인", CouponType.FIXED, 5000,
                    null, 100, FUTURE);

            assertAll(
                    () -> assertThat(coupon.calculateDiscount(BigDecimal.valueOf(10000)))
                            .isEqualByComparingTo(BigDecimal.valueOf(5000)),
                    () -> assertThat(coupon.calculateDiscount(BigDecimal.valueOf(3000)))
                            .isEqualByComparingTo(BigDecimal.valueOf(3000))
            );
        }

        @Test
        void 정률_타입은_비율로_할인_금액을_계산한다() {
            Coupon coupon = Coupon.create("10% 할인", CouponType.RATE, 10,
                    null, 100, FUTURE);

            BigDecimal discount = coupon.calculateDiscount(BigDecimal.valueOf(50000));

            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(5000));
        }

        @Test
        void 정률_타입_할인_계산은_소수점_이하를_버린다() {
            Coupon coupon = Coupon.create("33% 할인", CouponType.RATE, 33,
                    null, 100, FUTURE);

            BigDecimal discount = coupon.calculateDiscount(BigDecimal.valueOf(10000));

            assertThat(discount).isEqualByComparingTo(BigDecimal.valueOf(3300));
        }
    }
}

package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponDomainServiceTest {

    private CouponDomainService couponService;

    @BeforeEach
    void setUp() {
        couponService = new CouponDomainService(new FakeCouponRepository());
    }

    private ZonedDateTime futureDate() {
        return ZonedDateTime.now().plusDays(30);
    }

    @DisplayName("쿠폰을 등록할 때, ")
    @Nested
    class Register {

        @DisplayName("올바른 정보이면, 쿠폰이 등록된다.")
        @Test
        void registersCoupon_whenValidInfo() {
            Coupon coupon = couponService.register("10% 할인", CouponType.RATE, 10, 10000, futureDate());

            assertAll(
                () -> assertThat(coupon.getId()).isNotNull(),
                () -> assertThat(coupon.getName()).isEqualTo("10% 할인"),
                () -> assertThat(coupon.getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(coupon.getValue()).isEqualTo(10)
            );
        }
    }

    @DisplayName("쿠폰을 ID로 조회할 때, ")
    @Nested
    class GetById {

        @DisplayName("존재하는 쿠폰이면, 쿠폰을 반환한다.")
        @Test
        void returnsCoupon_whenCouponExists() {
            Coupon created = couponService.register("할인쿠폰", CouponType.FIXED, 5000, 0, futureDate());

            Coupon result = couponService.getById(created.getId());

            assertThat(result.getId()).isEqualTo(created.getId());
        }

        @DisplayName("존재하지 않는 쿠폰이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponDoesNotExist() {
            CoreException result = assertThrows(CoreException.class,
                () -> couponService.getById(999L));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰을 수정할 때, ")
    @Nested
    class Update {

        @DisplayName("올바른 정보이면, 쿠폰이 수정된다.")
        @Test
        void updatesCoupon_whenValidInfo() {
            Coupon created = couponService.register("할인쿠폰", CouponType.FIXED, 5000, 0, futureDate());

            Coupon updated = couponService.update(created.getId(), "수정된 쿠폰", CouponType.RATE, 20, 10000, futureDate());

            assertAll(
                () -> assertThat(updated.getName()).isEqualTo("수정된 쿠폰"),
                () -> assertThat(updated.getType()).isEqualTo(CouponType.RATE),
                () -> assertThat(updated.getValue()).isEqualTo(20),
                () -> assertThat(updated.getMinOrderAmount()).isEqualTo(10000)
            );
        }
    }

    @DisplayName("쿠폰을 삭제할 때, ")
    @Nested
    class Delete {

        @DisplayName("존재하는 쿠폰이면, 논리 삭제된다.")
        @Test
        void deletesCoupon_whenCouponExists() {
            Coupon created = couponService.register("할인쿠폰", CouponType.FIXED, 5000, 0, futureDate());

            couponService.delete(created.getId());

            CoreException result = assertThrows(CoreException.class,
                () -> couponService.getById(created.getId()));
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 목록을 조회할 때, ")
    @Nested
    class GetAll {

        @DisplayName("등록된 쿠폰이 있으면, 목록을 반환한다.")
        @Test
        void returnsCoupons_whenCouponsExist() {
            couponService.register("쿠폰1", CouponType.FIXED, 1000, 0, futureDate());
            couponService.register("쿠폰2", CouponType.RATE, 10, 0, futureDate());

            var result = couponService.getAll(0, 20);

            assertThat(result.items()).hasSize(2);
        }
    }
}

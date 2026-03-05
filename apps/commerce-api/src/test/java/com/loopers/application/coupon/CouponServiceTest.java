package com.loopers.application.coupon;

import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.InMemoryCouponRepository;
import com.loopers.domain.coupon.InMemoryIssuedCouponRepository;
import com.loopers.domain.user.InMemoryUserRepository;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserFixture;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class CouponServiceTest {

    private InMemoryCouponRepository couponRepository;
    private InMemoryIssuedCouponRepository issuedCouponRepository;
    private CouponService couponService;

    private User savedUser;

    @BeforeEach
    void setUp() {
        InMemoryUserRepository userRepository = new InMemoryUserRepository();
        couponRepository = new InMemoryCouponRepository();
        issuedCouponRepository = new InMemoryIssuedCouponRepository();
        couponService = new CouponService(couponRepository, issuedCouponRepository);

        savedUser = UserFixture.builder().build();
        userRepository.save(savedUser);
    }

    @DisplayName("쿠폰 발급 시,")
    @Nested
    class Issue {

        @DisplayName("유효한 쿠폰과 사용자이면 발급된 쿠폰 정보를 반환한다.")
        @Test
        void returnsIssuedCouponInfo_whenValid() {
            // arrange
            Coupon savedCoupon = couponRepository.save(
                Coupon.create("신규 회원 쿠폰", Coupon.DiscountType.FIXED, 1000L, 0L, LocalDateTime.now().plusDays(30))
            );

            // act
            IssuedCouponInfo result = couponService.issue(savedUser.getId(), savedCoupon.getId());

            // assert
            assertThat(result.couponId()).isEqualTo(savedCoupon.getId());
            assertThat(result.userId()).isEqualTo(savedUser.getId());
        }

        @DisplayName("존재하지 않는 쿠폰이면 NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenCouponNotExists() {
            // arrange
            long invalidCouponId = Long.MAX_VALUE;

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                couponService.issue(savedUser.getId(), invalidCouponId)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("이미 발급된 쿠폰이면 CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenAlreadyIssued() {
            // arrange
            Coupon savedCoupon = couponRepository.save(
                Coupon.create("신규 회원 쿠폰", Coupon.DiscountType.FIXED, 1000L, 0L, LocalDateTime.now().plusDays(30))
            );
            couponService.issue(savedUser.getId(), savedCoupon.getId());

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                couponService.issue(savedUser.getId(), savedCoupon.getId())
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("내 쿠폰 목록 조회 시,")
    @Nested
    class GetIssuedCoupons {

        @DisplayName("발급된 쿠폰이 있으면 해당 쿠폰 ID를 포함한 목록을 반환한다.")
        @Test
        void returnsList_withIssuedCouponIds() {
            // arrange
            Coupon coupon1 = couponRepository.save(
                Coupon.create("정액 할인 쿠폰", Coupon.DiscountType.FIXED, 1000L, 0L, LocalDateTime.now().plusDays(30))
            );
            Coupon coupon2 = couponRepository.save(
                Coupon.create("정률 할인 쿠폰", Coupon.DiscountType.RATE, 10L, 0L, LocalDateTime.now().plusDays(30))
            );
            couponService.issue(savedUser.getId(), coupon1.getId());
            couponService.issue(savedUser.getId(), coupon2.getId());

            // act
            List<IssuedCouponInfo> result = couponService.getIssuedCoupons(savedUser.getId());

            // assert
            assertThat(result)
                .extracting(IssuedCouponInfo::couponId)
                .containsExactlyInAnyOrder(coupon1.getId(), coupon2.getId());
        }

        @DisplayName("발급된 쿠폰이 없으면 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoIssuedCoupons() {
            // act
            List<IssuedCouponInfo> result = couponService.getIssuedCoupons(savedUser.getId());

            // assert
            assertThat(result).isEmpty();
        }
    }
}

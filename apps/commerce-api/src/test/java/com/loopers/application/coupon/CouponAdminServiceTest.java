package com.loopers.application.coupon;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponType;
import com.loopers.fake.FakeCouponTemplateRepository;
import com.loopers.fake.FakeIssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponAdminServiceTest {

    private CouponAdminService couponAdminService;
    private FakeCouponTemplateRepository couponTemplateRepository;
    private FakeIssuedCouponRepository issuedCouponRepository;

    @BeforeEach
    void setUp() {
        couponTemplateRepository = new FakeCouponTemplateRepository();
        issuedCouponRepository = new FakeIssuedCouponRepository();
        couponAdminService = new CouponAdminService(couponTemplateRepository, issuedCouponRepository);
    }

    @DisplayName("쿠폰을 생성할 때,")
    @Nested
    class CreateCoupon {

        @DisplayName("정액 쿠폰이 정상적으로 생성된다.")
        @Test
        void createsFixedCoupon() {
            // arrange
            var request = new CreateCouponRequest(
                "5000원 할인 쿠폰",
                CouponType.FIXED,
                5000L,
                10000L,
                null,
                100,
                ZonedDateTime.now().plusDays(30)
            );

            // act
            CouponTemplateResult result = couponAdminService.create(request);

            // assert
            assertThat(result.id()).isNotNull();
            assertThat(result.name()).isEqualTo("5000원 할인 쿠폰");
            assertThat(result.type()).isEqualTo(CouponType.FIXED);
            assertThat(result.value()).isEqualTo(5000L);
            assertThat(result.minOrderAmount()).isEqualTo(10000L);
            assertThat(result.maxIssueCount()).isEqualTo(100);
        }

        @DisplayName("정률 쿠폰이 정상적으로 생성된다.")
        @Test
        void createsRateCoupon() {
            // arrange
            var request = new CreateCouponRequest(
                "10% 할인 쿠폰",
                CouponType.RATE,
                10L,  // 10%
                20000L,
                5000,  // 최대 할인 5000원
                50,
                ZonedDateTime.now().plusDays(14)
            );

            // act
            CouponTemplateResult result = couponAdminService.create(request);

            // assert
            assertThat(result.id()).isNotNull();
            assertThat(result.type()).isEqualTo(CouponType.RATE);
            assertThat(result.value()).isEqualTo(1000L); // 10% = 1000
            assertThat(result.maxDiscountAmount()).isEqualTo(5000);
        }
    }

    @DisplayName("쿠폰을 조회할 때,")
    @Nested
    class FindCoupon {

        @DisplayName("존재하는 쿠폰이 조회된다.")
        @Test
        void findsCoupon_whenExists() {
            // arrange
            CouponTemplate saved = couponTemplateRepository.save(
                CouponTemplate.createFixed("테스트 쿠폰", new Money(3000), Money.ZERO, 10, ZonedDateTime.now().plusDays(7))
            );

            // act
            CouponTemplateResult result = couponAdminService.findById(saved.getId());

            // assert
            assertThat(result.id()).isEqualTo(saved.getId());
            assertThat(result.name()).isEqualTo("테스트 쿠폰");
        }

        @DisplayName("존재하지 않는 쿠폰 조회 시 예외가 발생한다.")
        @Test
        void throwsException_whenNotFound() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> couponAdminService.findById(999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_NOT_FOUND);
        }
    }

    @DisplayName("쿠폰 목록을 조회할 때,")
    @Nested
    class FindAllCoupons {

        @DisplayName("활성 쿠폰 목록이 조회된다.")
        @Test
        void findsActiveCoupons() {
            // arrange
            couponTemplateRepository.save(
                CouponTemplate.createFixed("쿠폰1", new Money(1000), Money.ZERO, 10, ZonedDateTime.now().plusDays(7))
            );
            couponTemplateRepository.save(
                CouponTemplate.createFixed("쿠폰2", new Money(2000), Money.ZERO, 20, ZonedDateTime.now().plusDays(14))
            );
            CouponTemplate deletedCoupon = couponTemplateRepository.save(
                CouponTemplate.createFixed("삭제된 쿠폰", new Money(3000), Money.ZERO, 30, ZonedDateTime.now().plusDays(30))
            );
            deletedCoupon.delete();
            couponTemplateRepository.save(deletedCoupon);

            // act
            Page<CouponTemplateResult> result = couponAdminService.findAll(PageRequest.of(0, 10));

            // assert
            assertThat(result.getTotalElements()).isEqualTo(2);
        }
    }

    @DisplayName("쿠폰을 삭제할 때,")
    @Nested
    class DeleteCoupon {

        @DisplayName("존재하는 쿠폰이 삭제된다.")
        @Test
        void deletesCoupon_whenExists() {
            // arrange
            CouponTemplate saved = couponTemplateRepository.save(
                CouponTemplate.createFixed("삭제 대상 쿠폰", new Money(5000), Money.ZERO, 10, ZonedDateTime.now().plusDays(7))
            );

            // act
            couponAdminService.delete(saved.getId());

            // assert
            CouponTemplate deleted = couponTemplateRepository.findById(saved.getId()).orElseThrow();
            assertThat(deleted.isDeleted()).isTrue();
        }

        @DisplayName("존재하지 않는 쿠폰 삭제 시 예외가 발생한다.")
        @Test
        void throwsException_whenNotFound() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> couponAdminService.delete(999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_NOT_FOUND);
        }
    }
}

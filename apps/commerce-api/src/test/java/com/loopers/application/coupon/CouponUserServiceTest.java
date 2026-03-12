package com.loopers.application.coupon;

import com.loopers.domain.common.Money;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.coupon.IssuedCouponStatus;
import com.loopers.fake.FakeCouponTemplateRepository;
import com.loopers.fake.FakeIssuedCouponRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CouponUserServiceTest {

    private CouponUserService couponUserService;
    private FakeCouponTemplateRepository couponTemplateRepository;
    private FakeIssuedCouponRepository issuedCouponRepository;

    @BeforeEach
    void setUp() {
        couponTemplateRepository = new FakeCouponTemplateRepository();
        issuedCouponRepository = new FakeIssuedCouponRepository();
        couponUserService = new CouponUserService(couponTemplateRepository, issuedCouponRepository);
    }

    @DisplayName("쿠폰을 발급받을 때,")
    @Nested
    class IssueCoupon {

        @DisplayName("유효한 쿠폰이 정상적으로 발급된다.")
        @Test
        void issuesCoupon_whenValid() {
            // arrange
            Long userId = 1L;
            CouponTemplate template = couponTemplateRepository.save(
                CouponTemplate.createFixed("테스트 쿠폰", new Money(5000), Money.ZERO, 10, ZonedDateTime.now().plusDays(7))
            );

            // act
            IssuedCouponResult result = couponUserService.issue(userId, template.getId());

            // assert
            assertThat(result.id()).isNotNull();
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.couponTemplateId()).isEqualTo(template.getId());
            assertThat(result.status()).isEqualTo(IssuedCouponStatus.AVAILABLE);
            assertThat(result.usable()).isTrue();
        }

        @DisplayName("발급 시 템플릿의 발급 수가 증가한다.")
        @Test
        void incrementsIssuedCount() {
            // arrange
            Long userId = 1L;
            CouponTemplate template = couponTemplateRepository.save(
                CouponTemplate.createFixed("테스트 쿠폰", new Money(5000), Money.ZERO, 10, ZonedDateTime.now().plusDays(7))
            );

            // act
            couponUserService.issue(userId, template.getId());

            // assert
            CouponTemplate updated = couponTemplateRepository.findById(template.getId()).orElseThrow();
            assertThat(updated.getIssuedCount()).isEqualTo(1);
        }

        @DisplayName("존재하지 않는 쿠폰 발급 시 예외가 발생한다.")
        @Test
        void throwsException_whenCouponNotFound() {
            // act
            CoreException result = assertThrows(CoreException.class,
                () -> couponUserService.issue(1L, 999L));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_NOT_FOUND);
        }

        @DisplayName("이미 발급받은 쿠폰 재발급 시 예외가 발생한다.")
        @Test
        void throwsException_whenAlreadyIssued() {
            // arrange
            Long userId = 1L;
            CouponTemplate template = couponTemplateRepository.save(
                CouponTemplate.createFixed("테스트 쿠폰", new Money(5000), Money.ZERO, 10, ZonedDateTime.now().plusDays(7))
            );
            couponUserService.issue(userId, template.getId());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> couponUserService.issue(userId, template.getId()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_ALREADY_ISSUED);
        }

        @DisplayName("발급 한도에 도달한 쿠폰 발급 시 예외가 발생한다.")
        @Test
        void throwsException_whenExhausted() {
            // arrange
            CouponTemplate template = CouponTemplate.reconstitute(
                1L, "한도 도달 쿠폰", com.loopers.domain.coupon.CouponType.FIXED,
                new Money(5000), Money.ZERO, null, 1, 1,
                ZonedDateTime.now().plusDays(7), ZonedDateTime.now(), null
            );
            couponTemplateRepository.save(template);

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> couponUserService.issue(1L, template.getId()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_EXHAUSTED);
        }
    }

    @DisplayName("내 쿠폰 목록을 조회할 때,")
    @Nested
    class FindMyCoupons {

        @DisplayName("발급받은 쿠폰 목록이 조회된다.")
        @Test
        void findsMyCoupons() {
            // arrange
            Long userId = 1L;
            CouponTemplate template1 = couponTemplateRepository.save(
                CouponTemplate.createFixed("쿠폰1", new Money(1000), Money.ZERO, 100, ZonedDateTime.now().plusDays(7))
            );
            CouponTemplate template2 = couponTemplateRepository.save(
                CouponTemplate.createFixed("쿠폰2", new Money(2000), Money.ZERO, 100, ZonedDateTime.now().plusDays(14))
            );

            couponUserService.issue(userId, template1.getId());
            couponUserService.issue(userId, template2.getId());

            // act
            List<IssuedCouponResult> result = couponUserService.findMyCoupons(userId, 0, 10);

            // assert
            assertThat(result).hasSize(2);
        }

        @DisplayName("다른 사용자의 쿠폰은 조회되지 않는다.")
        @Test
        void doesNotFindOtherUsersCoupons() {
            // arrange
            CouponTemplate template = couponTemplateRepository.save(
                CouponTemplate.createFixed("쿠폰", new Money(1000), Money.ZERO, 100, ZonedDateTime.now().plusDays(7))
            );
            couponUserService.issue(1L, template.getId());

            // act
            List<IssuedCouponResult> result = couponUserService.findMyCoupons(2L, 0, 10);

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("쿠폰 상세를 조회할 때,")
    @Nested
    class FindCouponDetail {

        @DisplayName("본인 쿠폰이 정상적으로 조회된다.")
        @Test
        void findsCouponDetail() {
            // arrange
            Long userId = 1L;
            CouponTemplate template = couponTemplateRepository.save(
                CouponTemplate.createFixed("테스트 쿠폰", new Money(5000), Money.ZERO, 10, ZonedDateTime.now().plusDays(7))
            );
            IssuedCouponResult issued = couponUserService.issue(userId, template.getId());

            // act
            IssuedCouponResult result = couponUserService.findMyCouponById(userId, issued.id());

            // assert
            assertThat(result.id()).isEqualTo(issued.id());
            assertThat(result.couponName()).isEqualTo("테스트 쿠폰");
        }

        @DisplayName("다른 사용자의 쿠폰 조회 시 예외가 발생한다.")
        @Test
        void throwsException_whenAccessDenied() {
            // arrange
            CouponTemplate template = couponTemplateRepository.save(
                CouponTemplate.createFixed("테스트 쿠폰", new Money(5000), Money.ZERO, 10, ZonedDateTime.now().plusDays(7))
            );
            IssuedCouponResult issued = couponUserService.issue(1L, template.getId());

            // act
            CoreException result = assertThrows(CoreException.class,
                () -> couponUserService.findMyCouponById(2L, issued.id()));

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.COUPON_ACCESS_DENIED);
        }
    }
}

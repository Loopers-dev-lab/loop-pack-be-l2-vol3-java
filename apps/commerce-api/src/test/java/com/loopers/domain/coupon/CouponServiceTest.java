package com.loopers.domain.coupon;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.CouponErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponServiceTest {

    private CouponTemplateRepository couponTemplateRepository;
    private IssuedCouponRepository issuedCouponRepository;
    private CouponService couponService;

    @BeforeEach
    void setUp() {
        couponTemplateRepository = Mockito.mock(CouponTemplateRepository.class);
        issuedCouponRepository = Mockito.mock(IssuedCouponRepository.class);
        couponService = new CouponService(couponTemplateRepository, issuedCouponRepository);
    }

    private CouponTemplate createActiveTemplate() {
        return CouponTemplate.define(
                "신규 가입 쿠폰", "신규 가입 시 5000원 할인", DiscountType.FIXED, 5000, null,
                10000, 100, 1,
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30)
        );
    }

    @DisplayName("쿠폰을 발급할 때,")
    @Nested
    class 발급 {

        @Test
        void 존재하지_않는_템플릿이면_예외가_발생한다() {
            // arrange
            when(couponTemplateRepository.findByIdForUpdate(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> couponService.issue(1L, 1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CouponErrorType.TEMPLATE_NOT_FOUND);
        }

        @Test
        void 유효하지_않은_템플릿이면_예외가_발생한다() {
            // arrange
            CouponTemplate template = createActiveTemplate();
            template.changeStatus(CouponTemplateStatus.INACTIVE);
            when(couponTemplateRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(template));

            // act & assert
            assertThatThrownBy(() -> couponService.issue(1L, 1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CouponErrorType.INVALID_TEMPLATE);
        }

        @Test
        void 전체_발급_제한_초과면_예외가_발생한다() {
            // arrange — issuedCount가 maxIssueCount에 도달한 템플릿
            CouponTemplate template = CouponTemplate.reconstitute(
                    1L, "신규 가입 쿠폰", "설명", DiscountType.FIXED, 5000, null,
                    10000, 100, 1, 100,  // issuedCount=100, maxIssueCount=100
                    ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30),
                    CouponTemplateStatus.ACTIVE, ZonedDateTime.now(), ZonedDateTime.now(), null
            );
            when(couponTemplateRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(template));

            // act & assert
            assertThatThrownBy(() -> couponService.issue(1L, 1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CouponErrorType.ISSUE_LIMIT_EXCEEDED);
        }

        @Test
        void 유저별_발급_제한_초과면_예외가_발생한다() {
            // arrange
            CouponTemplate template = createActiveTemplate();
            when(couponTemplateRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(template));
            when(issuedCouponRepository.countByCouponTemplateId(1L)).thenReturn(50L);
            when(issuedCouponRepository.countByCouponTemplateIdAndUserId(1L, 1L)).thenReturn(1L);

            // act & assert
            assertThatThrownBy(() -> couponService.issue(1L, 1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CouponErrorType.USER_ISSUE_LIMIT_EXCEEDED);
        }

        @Test
        void 유효한_요청이면_ISSUED_상태로_발급된다() {
            // arrange
            CouponTemplate template = createActiveTemplate();
            when(couponTemplateRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(template));
            when(issuedCouponRepository.countByCouponTemplateId(1L)).thenReturn(50L);
            when(issuedCouponRepository.countByCouponTemplateIdAndUserId(1L, 1L)).thenReturn(0L);
            when(issuedCouponRepository.save(any(IssuedCoupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            IssuedCoupon result = couponService.issue(1L, 1L);

            // assert
            assertThat(result.getStatus()).isEqualTo(IssuedCouponStatus.ISSUED);
        }

        @Test
        void 발급_시_save가_호출된다() {
            // arrange
            CouponTemplate template = createActiveTemplate();
            when(couponTemplateRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(template));
            when(issuedCouponRepository.countByCouponTemplateId(1L)).thenReturn(50L);
            when(issuedCouponRepository.countByCouponTemplateIdAndUserId(1L, 1L)).thenReturn(0L);
            when(issuedCouponRepository.save(any(IssuedCoupon.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // act
            couponService.issue(1L, 1L);

            // assert
            verify(issuedCouponRepository).save(any(IssuedCoupon.class));
        }
    }

    @DisplayName("쿠폰을 사용할 때,")
    @Nested
    class 사용 {

        @Test
        void 존재하지_않는_쿠폰이면_예외가_발생한다() {
            // arrange
            when(issuedCouponRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> couponService.use(1L, 1L, 100L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CouponErrorType.COUPON_NOT_FOUND);
        }

        @Test
        void ISSUED가_아니면_예외가_발생한다() {
            // arrange — EXPIRED 상태로 전이
            IssuedCoupon coupon = IssuedCoupon.issue(1L, 1L, "테스트쿠폰", DiscountType.FIXED, 5000, null);
            coupon.expire();
            when(issuedCouponRepository.findById(1L)).thenReturn(Optional.of(coupon));

            // act & assert
            assertThatThrownBy(() -> couponService.use(1L, 1L, 100L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CouponErrorType.INVALID_COUPON_STATUS);
        }

        @Test
        void 원자적_UPDATE가_0이면_이미_사용된_쿠폰_예외가_발생한다() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.issue(1L, 1L, "테스트쿠폰", DiscountType.FIXED, 5000, null);
            when(issuedCouponRepository.findById(1L)).thenReturn(Optional.of(coupon));
            when(issuedCouponRepository.useAtomically(anyLong(), anyLong(), any(ZonedDateTime.class))).thenReturn(0);

            // act & assert
            assertThatThrownBy(() -> couponService.use(1L, 1L, 100L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CouponErrorType.COUPON_ALREADY_USED);
        }

        @Test
        void 유효한_요청이면_원자적_UPDATE가_호출된다() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.issue(1L, 1L, "테스트쿠폰", DiscountType.FIXED, 5000, null);
            when(issuedCouponRepository.findById(1L)).thenReturn(Optional.of(coupon));
            when(issuedCouponRepository.useAtomically(anyLong(), anyLong(), any(ZonedDateTime.class))).thenReturn(1);

            // act
            couponService.use(1L, 1L, 100L);

            // assert
            verify(issuedCouponRepository).useAtomically(anyLong(), anyLong(), any(ZonedDateTime.class));
        }
    }

    @DisplayName("내 쿠폰을 조회할 때,")
    @Nested
    class 내_쿠폰_조회 {

        @Test
        void 사용자의_쿠폰_목록을_반환한다() {
            // arrange
            IssuedCoupon coupon = IssuedCoupon.issue(1L, 1L, "테스트쿠폰", DiscountType.FIXED, 5000, null);
            when(issuedCouponRepository.findAllByUserId(1L)).thenReturn(List.of(coupon));

            // act
            List<IssuedCoupon> result = couponService.getUserCoupons(1L);

            // assert
            assertThat(result).hasSize(1);
        }
    }

    @DisplayName("쿠폰 템플릿을 조회할 때,")
    @Nested
    class 템플릿_조회 {

        @Test
        void 존재하지_않으면_예외가_발생한다() {
            // arrange
            when(couponTemplateRepository.findById(1L)).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> couponService.getTemplate(1L))
                    .isInstanceOf(CoreException.class)
                    .extracting(e -> ((CoreException) e).getErrorType())
                    .isEqualTo(CouponErrorType.TEMPLATE_NOT_FOUND);
        }

        @Test
        void 존재하면_반환한다() {
            // arrange
            CouponTemplate template = createActiveTemplate();
            when(couponTemplateRepository.findById(1L)).thenReturn(Optional.of(template));

            // act
            CouponTemplate result = couponService.getTemplate(1L);

            // assert
            assertThat(result.getName()).isEqualTo("신규 가입 쿠폰");
        }
    }
}

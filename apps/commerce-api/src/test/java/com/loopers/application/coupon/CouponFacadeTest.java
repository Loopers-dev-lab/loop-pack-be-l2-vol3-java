package com.loopers.application.coupon;

import com.loopers.application.coupon.dto.FindMyCouponResDto;
import com.loopers.application.coupon.dto.IssueCouponResDto;
import com.loopers.domain.coupon.model.UserCoupon;
import com.loopers.domain.coupon.model.UserCouponItem;
import com.loopers.domain.coupon.service.CouponService;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.service.MemberService;
import com.loopers.support.CouponEnums;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponFacade 테스트")
class CouponFacadeTest {

    @InjectMocks
    private CouponFacade couponFacade;

    @Mock
    private CouponService couponService;

    @Mock
    private MemberService memberService;

    @Mock
    private Member member;

    @Nested
    @DisplayName("쿠폰 발급 시")
    class IssueCoupon {

        @Test
        @DisplayName("인증 후 성공적으로 발급한다")
        void success() {
            // arrange
            given(memberService.findMember("user1", "pass1")).willReturn(member);
            given(member.getId()).willReturn(100L);
            UserCoupon userCoupon = UserCoupon.issue(1L, 100L);
            given(couponService.issueCoupon(1L, 100L)).willReturn(userCoupon);

            // act
            IssueCouponResDto result = couponFacade.issueCoupon("user1", "pass1", 1L);

            // assert
            assertThat(result.couponTemplateId()).isEqualTo(1L);
            assertThat(result.status()).isEqualTo("AVAILABLE");
        }

        @Test
        @DisplayName("인증 실패 시 UNAUTHORIZED 예외가 발생한다")
        void failWhenUnauthorized() {
            // arrange
            given(memberService.findMember("wrong", "wrong"))
                    .willThrow(new CoreException(ErrorType.UNAUTHORIZED));

            // act & assert
            assertThatThrownBy(() -> couponFacade.issueCoupon("wrong", "wrong", 1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }
    }

    @Nested
    @DisplayName("내 쿠폰 목록 조회 시")
    class GetMyCoupons {

        @Test
        @DisplayName("인증 후 쿠폰 목록을 반환한다")
        void success() {
            // arrange
            given(memberService.findMember("user1", "pass1")).willReturn(member);
            given(member.getId()).willReturn(100L);
            Pageable pageable = PageRequest.of(0, 20);
            UserCouponItem item = new UserCouponItem(
                    1L, 1L, "테스트 쿠폰", CouponEnums.Type.FIXED, 1000, 10000,
                    CouponEnums.Status.AVAILABLE, LocalDateTime.now().plusDays(30), null);
            Page<UserCouponItem> page = new PageImpl<>(List.of(item), pageable, 1);
            given(couponService.getUserCouponsWithTemplate(100L, pageable)).willReturn(page);

            // act
            Page<FindMyCouponResDto> result = couponFacade.getMyCoupons("user1", "pass1", pageable);

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).couponName()).isEqualTo("테스트 쿠폰");
        }

        @Test
        @DisplayName("인증 실패 시 UNAUTHORIZED 예외가 발생한다")
        void failWhenUnauthorized() {
            // arrange
            given(memberService.findMember("wrong", "wrong"))
                    .willThrow(new CoreException(ErrorType.UNAUTHORIZED));

            // act & assert
            assertThatThrownBy(() -> couponFacade.getMyCoupons("wrong", "wrong", PageRequest.of(0, 20)))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }
    }
}

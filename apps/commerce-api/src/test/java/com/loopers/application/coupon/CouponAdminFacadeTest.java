package com.loopers.application.coupon;

import com.loopers.application.coupon.dto.FindCouponTemplateResDto;
import com.loopers.application.coupon.dto.FindIssuedCouponResDto;
import com.loopers.domain.coupon.model.CouponCommand;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.UserCoupon;
import com.loopers.domain.coupon.service.CouponService;
import com.loopers.support.CouponEnums;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponAdminFacade 테스트")
class CouponAdminFacadeTest {

    @InjectMocks
    private CouponAdminFacade couponAdminFacade;

    @Mock
    private CouponService couponService;

    private CouponTemplate createTestTemplate() {
        return CouponTemplate.reconstruct(1L, "테스트 쿠폰", "FIXED", 1000, 10000,
                LocalDateTime.now().plusDays(30));
    }

    @Nested
    @DisplayName("템플릿 생성 시")
    class CreateTemplate {

        @Test
        @DisplayName("성공적으로 생성하고 DTO를 반환한다")
        void success() {
            // arrange
            CouponCommand.CreateTemplate command = new CouponCommand.CreateTemplate(
                    "쿠폰", CouponEnums.Type.FIXED, 1000, 0, LocalDateTime.now().plusDays(30));
            given(couponService.createTemplate(command)).willReturn(createTestTemplate());

            // act
            FindCouponTemplateResDto result = couponAdminFacade.createTemplate(command);

            // assert
            assertThat(result.name()).isEqualTo("테스트 쿠폰");
        }
    }

    @Nested
    @DisplayName("템플릿 조회 시")
    class GetTemplate {

        @Test
        @DisplayName("DTO로 변환하여 반환한다")
        void success() {
            // arrange
            given(couponService.getTemplate(1L)).willReturn(createTestTemplate());

            // act
            FindCouponTemplateResDto result = couponAdminFacade.getTemplate(1L);

            // assert
            assertThat(result.id()).isEqualTo(1L);
            assertThat(result.type()).isEqualTo("FIXED");
        }
    }

    @Nested
    @DisplayName("템플릿 목록 조회 시")
    class GetTemplates {

        @Test
        @DisplayName("페이징 DTO를 반환한다")
        void success() {
            // arrange
            Pageable pageable = PageRequest.of(0, 20);
            Page<CouponTemplate> page = new PageImpl<>(List.of(createTestTemplate()), pageable, 1);
            given(couponService.getTemplates(pageable)).willReturn(page);

            // act
            Page<FindCouponTemplateResDto> result = couponAdminFacade.getTemplates(pageable);

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("템플릿 삭제 시")
    class DeleteTemplate {

        @Test
        @DisplayName("성공적으로 삭제한다")
        void success() {
            // act
            couponAdminFacade.deleteTemplate(1L);

            // assert
            verify(couponService).deleteTemplate(1L);
        }
    }

    @Nested
    @DisplayName("발급 내역 조회 시")
    class GetIssuedCoupons {

        @Test
        @DisplayName("페이징 DTO를 반환한다")
        void success() {
            // arrange
            Pageable pageable = PageRequest.of(0, 20);
            UserCoupon userCoupon = UserCoupon.reconstruct(1L, 1L, 100L, "AVAILABLE", null);
            Page<UserCoupon> page = new PageImpl<>(List.of(userCoupon), pageable, 1);
            given(couponService.getIssuedCoupons(1L, pageable)).willReturn(page);

            // act
            Page<FindIssuedCouponResDto> result = couponAdminFacade.getIssuedCoupons(1L, pageable);

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).memberId()).isEqualTo(100L);
        }
    }
}

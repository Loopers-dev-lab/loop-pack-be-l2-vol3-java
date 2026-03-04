package com.loopers.domain.coupon;

import com.loopers.domain.coupon.model.CouponCommand;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.domain.coupon.model.UserCoupon;
import com.loopers.domain.coupon.model.UserCouponItem;
import com.loopers.domain.coupon.repository.CouponTemplateRepository;
import com.loopers.domain.coupon.repository.UserCouponRepository;
import com.loopers.domain.coupon.service.CouponService;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CouponService 테스트")
class CouponServiceTest {

    @InjectMocks
    private CouponService couponService;

    @Mock
    private CouponTemplateRepository couponTemplateRepository;

    @Mock
    private UserCouponRepository userCouponRepository;

    private CouponTemplate createTestTemplate() {
        return CouponTemplate.reconstruct(1L, "테스트 쿠폰", "FIXED", 1000, 10000,
                LocalDateTime.now().plusDays(30));
    }

    private CouponTemplate createExpiredTemplate() {
        return CouponTemplate.reconstruct(1L, "만료 쿠폰", "FIXED", 1000, 0,
                LocalDateTime.now().minusDays(1));
    }

    @Nested
    @DisplayName("템플릿 생성 시")
    class CreateTemplate {

        @Test
        @DisplayName("성공적으로 생성한다")
        void success() {
            // arrange
            CouponCommand.CreateTemplate command = new CouponCommand.CreateTemplate(
                    "쿠폰", CouponEnums.Type.FIXED, 1000, 0, LocalDateTime.now().plusDays(30));
            given(couponTemplateRepository.save(any())).willReturn(createTestTemplate());

            // act
            CouponTemplate result = couponService.createTemplate(command);

            // assert
            assertThat(result).isNotNull();
            verify(couponTemplateRepository).save(any());
        }
    }

    @Nested
    @DisplayName("템플릿 조회 시")
    class GetTemplate {

        @Test
        @DisplayName("존재하면 반환한다")
        void success() {
            // arrange
            given(couponTemplateRepository.findById(1L)).willReturn(Optional.of(createTestTemplate()));

            // act
            CouponTemplate result = couponService.getTemplate(1L);

            // assert
            assertThat(result.getId()).isEqualTo(1L);
        }

        @Test
        @DisplayName("존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void failWhenNotFound() {
            // arrange
            given(couponTemplateRepository.findById(1L)).willReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> couponService.getTemplate(1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("템플릿 목록 조회 시")
    class GetTemplates {

        @Test
        @DisplayName("페이징 결과를 반환한다")
        void success() {
            // arrange
            Pageable pageable = PageRequest.of(0, 20);
            Page<CouponTemplate> page = new PageImpl<>(List.of(createTestTemplate()), pageable, 1);
            given(couponTemplateRepository.findAll(pageable)).willReturn(page);

            // act
            Page<CouponTemplate> result = couponService.getTemplates(pageable);

            // assert
            assertThat(result.getTotalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("템플릿 수정 시")
    class UpdateTemplate {

        @Test
        @DisplayName("성공적으로 수정한다")
        void success() {
            // arrange
            given(couponTemplateRepository.findById(1L)).willReturn(Optional.of(createTestTemplate()));
            CouponCommand.UpdateTemplate command = new CouponCommand.UpdateTemplate(
                    "수정 쿠폰", CouponEnums.Type.RATE, 15, 5000, LocalDateTime.now().plusDays(60));

            // act
            CouponTemplate result = couponService.updateTemplate(1L, command);

            // assert
            assertThat(result.getName().value()).isEqualTo("수정 쿠폰");
            verify(couponTemplateRepository).update(any());
        }
    }

    @Nested
    @DisplayName("템플릿 삭제 시")
    class DeleteTemplate {

        @Test
        @DisplayName("성공적으로 삭제한다")
        void success() {
            // arrange
            given(couponTemplateRepository.findById(1L)).willReturn(Optional.of(createTestTemplate()));

            // act
            couponService.deleteTemplate(1L);

            // assert
            verify(couponTemplateRepository).deleteById(1L);
        }

        @Test
        @DisplayName("존재하지 않으면 NOT_FOUND 예외가 발생한다")
        void failWhenNotFound() {
            // arrange
            given(couponTemplateRepository.findById(1L)).willReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> couponService.deleteTemplate(1L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }
    }

    @Nested
    @DisplayName("쿠폰 발급 시")
    class IssueCoupon {

        @Test
        @DisplayName("성공적으로 발급한다")
        void success() {
            // arrange
            given(couponTemplateRepository.findById(1L)).willReturn(Optional.of(createTestTemplate()));
            given(userCouponRepository.existsByMemberIdAndCouponTemplateId(100L, 1L)).willReturn(false);
            given(userCouponRepository.save(any())).willReturn(UserCoupon.issue(1L, 100L));

            // act
            UserCoupon result = couponService.issueCoupon(1L, 100L);

            // assert
            assertThat(result.getCouponTemplateId()).isEqualTo(1L);
            assertThat(result.getMemberId()).isEqualTo(100L);
        }

        @Test
        @DisplayName("만료된 템플릿이면 BAD_REQUEST 예외가 발생한다")
        void failWhenExpired() {
            // arrange
            given(couponTemplateRepository.findById(1L)).willReturn(Optional.of(createExpiredTemplate()));

            // act & assert
            assertThatThrownBy(() -> couponService.issueCoupon(1L, 100L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("이미 발급받았으면 CONFLICT 예외가 발생한다")
        void failWhenDuplicate() {
            // arrange
            given(couponTemplateRepository.findById(1L)).willReturn(Optional.of(createTestTemplate()));
            given(userCouponRepository.existsByMemberIdAndCouponTemplateId(100L, 1L)).willReturn(true);

            // act & assert
            assertThatThrownBy(() -> couponService.issueCoupon(1L, 100L))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }
    }

    @Nested
    @DisplayName("쿠폰 사용 시")
    class UseUserCoupon {

        @Test
        @DisplayName("성공적으로 사용한다")
        void success() {
            // arrange
            UserCoupon userCoupon = UserCoupon.issue(1L, 100L);
            given(userCouponRepository.findByIdWithLock(1L)).willReturn(Optional.of(userCoupon));
            given(couponTemplateRepository.findById(1L)).willReturn(Optional.of(createTestTemplate()));

            // act
            CouponTemplate result = couponService.useUserCoupon(1L, 100L, 50000);

            // assert
            assertThat(result).isNotNull();
            verify(userCouponRepository).update(any());
        }

        @Test
        @DisplayName("존재하지 않는 쿠폰이면 NOT_FOUND 예외가 발생한다")
        void failWhenNotFound() {
            // arrange
            given(userCouponRepository.findByIdWithLock(1L)).willReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> couponService.useUserCoupon(1L, 100L, 50000))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
        }

        @Test
        @DisplayName("타인의 쿠폰이면 BAD_REQUEST 예외가 발생한다")
        void failWhenNotOwner() {
            // arrange
            UserCoupon userCoupon = UserCoupon.issue(1L, 100L);
            given(userCouponRepository.findByIdWithLock(1L)).willReturn(Optional.of(userCoupon));

            // act & assert
            assertThatThrownBy(() -> couponService.useUserCoupon(1L, 200L, 50000))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @Test
        @DisplayName("최소 주문 금액 미달이면 BAD_REQUEST 예외가 발생한다")
        void failWhenBelowMinOrderAmount() {
            // arrange
            UserCoupon userCoupon = UserCoupon.issue(1L, 100L);
            given(userCouponRepository.findByIdWithLock(1L)).willReturn(Optional.of(userCoupon));
            given(couponTemplateRepository.findById(1L)).willReturn(Optional.of(createTestTemplate()));

            // act & assert
            assertThatThrownBy(() -> couponService.useUserCoupon(1L, 100L, 5000))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}

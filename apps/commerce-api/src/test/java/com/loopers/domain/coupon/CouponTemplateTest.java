package com.loopers.domain.coupon;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponTemplateTest {

    private CouponTemplate createFixedTemplate() {
        return CouponTemplate.create(
                "신규 가입 쿠폰", "신규 가입 시 5000원 할인", DiscountType.FIXED, 5000, null,
                10000, 1000, 1,
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30)
        );
    }

    private CouponTemplate createPercentTemplate() {
        return CouponTemplate.create(
                "10% 할인 쿠폰", "주문 금액의 10% 할인", DiscountType.PERCENT, 10, 20000,
                30000, 500, 1,
                ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30)
        );
    }

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 유효한_정보면_ACTIVE_상태로_생성된다() {
            // act
            CouponTemplate template = createFixedTemplate();

            // assert
            assertThat(template.getStatus()).isEqualTo(CouponTemplateStatus.ACTIVE);
        }
    }

    @DisplayName("할인금액을 계산할 때,")
    @Nested
    class 할인금액계산 {

        @Test
        void FIXED_타입이면_할인금액을_그대로_반환한다() {
            // arrange
            CouponTemplate template = createFixedTemplate();

            // act & assert
            assertThat(template.calculateDiscount(50000)).isEqualTo(5000);
        }

        @Test
        void PERCENT_타입이면_주문금액의_비율을_반환한다() {
            // arrange
            CouponTemplate template = createPercentTemplate();

            // act & assert — 100000 * 10% = 10000, maxDiscount=20000이므로 10000
            assertThat(template.calculateDiscount(100000)).isEqualTo(10000);
        }

        @Test
        void PERCENT_타입에서_maxDiscountAmount를_초과하면_최대값을_반환한다() {
            // arrange
            CouponTemplate template = createPercentTemplate();

            // act & assert — 300000 * 10% = 30000, maxDiscount=20000이므로 20000
            assertThat(template.calculateDiscount(300000)).isEqualTo(20000);
        }
    }

    @DisplayName("적용 가능 여부를 확인할 때,")
    @Nested
    class 적용가능여부확인 {

        @Test
        void 최소주문금액_미달이면_false를_반환한다() {
            // arrange
            CouponTemplate template = createFixedTemplate();

            // act & assert — minOrderAmount=10000인데 5000
            assertThat(template.isApplicable(5000, ZonedDateTime.now())).isFalse();
        }

        @Test
        void 유효기간이_지났으면_false를_반환한다() {
            // arrange
            CouponTemplate template = createFixedTemplate();

            // act & assert
            assertThat(template.isApplicable(50000, ZonedDateTime.now().plusDays(60))).isFalse();
        }

        @Test
        void 조건_충족이면_true를_반환한다() {
            // arrange
            CouponTemplate template = createFixedTemplate();

            // act & assert
            assertThat(template.isApplicable(50000, ZonedDateTime.now())).isTrue();
        }
    }

    @DisplayName("수정할 때,")
    @Nested
    class 수정 {

        @Test
        void 유효한_정보면_필드가_변경된다() {
            // arrange
            CouponTemplate template = createFixedTemplate();

            // act
            template.changeDetails("수정된 쿠폰", "수정된 쿠폰 설명", DiscountType.PERCENT, 15, 10000, 20000);

            // assert
            assertThat(template)
                    .extracting(CouponTemplate::getName, CouponTemplate::getDiscountType,
                            CouponTemplate::getDiscountValue, CouponTemplate::getMaxDiscountAmount,
                            CouponTemplate::getMinOrderAmount)
                    .containsExactly("수정된 쿠폰", DiscountType.PERCENT, 15, 10000, 20000);
        }
    }
}

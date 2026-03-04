package com.loopers.domain.coupon;

import com.loopers.domain.coupon.model.CouponCommand;
import com.loopers.domain.coupon.model.CouponTemplate;
import com.loopers.support.CouponEnums;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("CouponTemplate 모델 테스트")
class CouponTemplateTest {

    private CouponCommand.CreateTemplate createCommand(String name, CouponEnums.Type type,
                                                        int discountValue, int minOrderAmount,
                                                        LocalDateTime expiredAt) {
        return new CouponCommand.CreateTemplate(name, type, discountValue, minOrderAmount, expiredAt);
    }

    @Nested
    @DisplayName("생성 시")
    class Create {

        @Test
        @DisplayName("정액 쿠폰을 생성할 수 있다")
        void successFixed() {
            CouponCommand.CreateTemplate command = createCommand("1000원 할인", CouponEnums.Type.FIXED,
                    1000, 10000, LocalDateTime.now().plusDays(30));

            CouponTemplate template = CouponTemplate.create(command);

            assertThat(template.getName().value()).isEqualTo("1000원 할인");
            assertThat(template.getType()).isEqualTo(CouponEnums.Type.FIXED);
            assertThat(template.getDiscountValue().value()).isEqualTo(1000);
        }

        @Test
        @DisplayName("정률 쿠폰을 생성할 수 있다")
        void successRate() {
            CouponCommand.CreateTemplate command = createCommand("10% 할인", CouponEnums.Type.RATE,
                    10, 5000, LocalDateTime.now().plusDays(30));

            CouponTemplate template = CouponTemplate.create(command);

            assertThat(template.getType()).isEqualTo(CouponEnums.Type.RATE);
            assertThat(template.getDiscountValue().value()).isEqualTo(10);
        }

        @Test
        @DisplayName("정률 할인 값이 100을 초과하면 예외가 발생한다")
        void failWhenRateExceeds100() {
            CouponCommand.CreateTemplate command = createCommand("101% 할인", CouponEnums.Type.RATE,
                    101, 0, LocalDateTime.now().plusDays(30));

            assertThatThrownBy(() -> CouponTemplate.create(command))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("만료일이 과거이면 예외가 발생한다")
        void failWhenExpiredAtIsPast() {
            CouponCommand.CreateTemplate command = createCommand("할인", CouponEnums.Type.FIXED,
                    1000, 0, LocalDateTime.now().minusDays(1));

            assertThatThrownBy(() -> CouponTemplate.create(command))
                    .isInstanceOf(CoreException.class);
        }

        @Test
        @DisplayName("만료일이 null이면 예외가 발생한다")
        void failWhenExpiredAtIsNull() {
            CouponCommand.CreateTemplate command = createCommand("할인", CouponEnums.Type.FIXED,
                    1000, 0, null);

            assertThatThrownBy(() -> CouponTemplate.create(command))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("수정 시")
    class Update {

        @Test
        @DisplayName("템플릿 정보를 수정할 수 있다")
        void success() {
            CouponTemplate template = CouponTemplate.create(
                    createCommand("기존 쿠폰", CouponEnums.Type.FIXED, 1000, 0, LocalDateTime.now().plusDays(30)));

            CouponCommand.UpdateTemplate updateCommand = new CouponCommand.UpdateTemplate(
                    "수정된 쿠폰", CouponEnums.Type.RATE, 20, 5000, LocalDateTime.now().plusDays(60));

            template.update(updateCommand);

            assertThat(template.getName().value()).isEqualTo("수정된 쿠폰");
            assertThat(template.getType()).isEqualTo(CouponEnums.Type.RATE);
            assertThat(template.getDiscountValue().value()).isEqualTo(20);
        }
    }

    @Nested
    @DisplayName("만료 확인 시")
    class IsExpired {

        @Test
        @DisplayName("만료일이 지나면 true를 반환한다")
        void returnTrueWhenExpired() {
            CouponTemplate template = CouponTemplate.reconstruct(
                    1L, "만료 쿠폰", "FIXED", 1000, 0, LocalDateTime.now().minusDays(1));

            assertThat(template.isExpired()).isTrue();
        }

        @Test
        @DisplayName("만료일 전이면 false를 반환한다")
        void returnFalseWhenNotExpired() {
            CouponTemplate template = CouponTemplate.reconstruct(
                    1L, "유효 쿠폰", "FIXED", 1000, 0, LocalDateTime.now().plusDays(30));

            assertThat(template.isExpired()).isFalse();
        }
    }

    @Nested
    @DisplayName("할인 계산 시")
    class CalculateDiscount {

        @Test
        @DisplayName("정액 할인은 할인 금액을 반환한다")
        void fixedDiscount() {
            CouponTemplate template = CouponTemplate.reconstruct(
                    1L, "1000원 할인", "FIXED", 1000, 0, LocalDateTime.now().plusDays(30));

            assertThat(template.calculateDiscount(50000)).isEqualTo(1000);
        }

        @Test
        @DisplayName("정액 할인이 주문 금액보다 크면 주문 금액을 반환한다")
        void fixedDiscountExceedsOrderAmount() {
            CouponTemplate template = CouponTemplate.reconstruct(
                    1L, "5000원 할인", "FIXED", 5000, 0, LocalDateTime.now().plusDays(30));

            assertThat(template.calculateDiscount(3000)).isEqualTo(3000);
        }

        @Test
        @DisplayName("정률 할인은 주문 금액의 비율을 반환한다")
        void rateDiscount() {
            CouponTemplate template = CouponTemplate.reconstruct(
                    1L, "10% 할인", "RATE", 10, 0, LocalDateTime.now().plusDays(30));

            assertThat(template.calculateDiscount(50000)).isEqualTo(5000);
        }
    }
}

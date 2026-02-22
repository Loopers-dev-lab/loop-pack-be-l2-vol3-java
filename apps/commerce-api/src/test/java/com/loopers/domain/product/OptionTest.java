package com.loopers.domain.product;

import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Option 도메인 테스트")
class OptionTest {

    @Nested
    @DisplayName("생성 테스트")
    class CreateTest {

        @Test
        @DisplayName("유효한 정보로 옵션을 생성할 수 있다")
        void createOption() {
            Option option = Option.create(1L, "기본 옵션", Money.of(0L), 100);

            assertThat(option.getId()).isNull();
            assertThat(option.getProductId()).isEqualTo(1L);
            assertThat(option.getName()).isEqualTo("기본 옵션");
            assertThat(option.getAdditionalPrice().getAmount()).isEqualByComparingTo("0");
            assertThat(option.getStock()).isEqualTo(100);
        }

        @Test
        @DisplayName("추가 가격이 있는 옵션을 생성할 수 있다")
        void createOptionWithAdditionalPrice() {
            Option option = Option.create(1L, "대용량 옵션", Money.of(5000L), 50);

            assertThat(option.getAdditionalPrice().getAmount()).isEqualByComparingTo("5000");
        }

        @Test
        @DisplayName("추가 가격이 null이면 0원으로 설정된다")
        void createOptionWithNullAdditionalPrice() {
            Option option = Option.create(1L, "기본 옵션", null, 100);

            assertThat(option.getAdditionalPrice().getAmount()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("상품 ID가 없으면 예외가 발생한다")
        void createWithNullProductIdThrowsException() {
            assertThatThrownBy(() -> Option.create(null, "옵션명", Money.of(0L), 100))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("상품 ID는 필수");
        }

        @Test
        @DisplayName("옵션명이 빈 값이면 예외가 발생한다")
        void createWithEmptyNameThrowsException() {
            assertThatThrownBy(() -> Option.create(1L, "", Money.of(0L), 100))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("옵션명은 필수");
        }

        @Test
        @DisplayName("재고가 음수이면 예외가 발생한다")
        void createWithNegativeStockThrowsException() {
            assertThatThrownBy(() -> Option.create(1L, "옵션명", Money.of(0L), -1))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("재고는 0 이상");
        }
    }

    @Nested
    @DisplayName("재고 차감 테스트")
    class DecreaseStockTest {

        @Test
        @DisplayName("재고 차감에 성공한다")
        void decreaseStockSuccess() {
            Option option = Option.create(1L, "옵션", Money.of(0L), 100);

            option.decreaseStock(30);

            assertThat(option.getStock()).isEqualTo(70);
        }

        @Test
        @DisplayName("재고를 0까지 차감할 수 있다")
        void decreaseStockToZero() {
            Option option = Option.create(1L, "옵션", Money.of(0L), 50);

            option.decreaseStock(50);

            assertThat(option.getStock()).isZero();
            assertThat(option.isSoldOut()).isTrue();
        }

        @Test
        @DisplayName("재고보다 많은 수량을 차감하면 예외가 발생한다")
        void decreaseStockInsufficientThrowsException() {
            Option option = Option.create(1L, "옵션", Money.of(0L), 10);

            assertThatThrownBy(() -> option.decreaseStock(15))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("재고가 부족합니다");
        }

        @Test
        @DisplayName("0 이하의 수량을 차감하면 예외가 발생한다")
        void decreaseStockZeroOrNegativeThrowsException() {
            Option option = Option.create(1L, "옵션", Money.of(0L), 100);

            assertThatThrownBy(() -> option.decreaseStock(0))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("차감 수량은 1 이상");

            assertThatThrownBy(() -> option.decreaseStock(-5))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("재고 증가 테스트")
    class IncreaseStockTest {

        @Test
        @DisplayName("재고 증가에 성공한다")
        void increaseStockSuccess() {
            Option option = Option.create(1L, "옵션", Money.of(0L), 100);

            option.increaseStock(50);

            assertThat(option.getStock()).isEqualTo(150);
        }

        @Test
        @DisplayName("0 이하의 수량을 증가하면 예외가 발생한다")
        void increaseStockZeroOrNegativeThrowsException() {
            Option option = Option.create(1L, "옵션", Money.of(0L), 100);

            assertThatThrownBy(() -> option.increaseStock(0))
                    .isInstanceOf(CoreException.class);
        }
    }

    @Nested
    @DisplayName("품절 여부 테스트")
    class SoldOutTest {

        @Test
        @DisplayName("재고가 0이면 품절이다")
        void isSoldOutWhenStockIsZero() {
            Option option = Option.create(1L, "옵션", Money.of(0L), 0);

            assertThat(option.isSoldOut()).isTrue();
        }

        @Test
        @DisplayName("재고가 있으면 품절이 아니다")
        void isNotSoldOutWhenStockExists() {
            Option option = Option.create(1L, "옵션", Money.of(0L), 1);

            assertThat(option.isSoldOut()).isFalse();
        }
    }

    @Nested
    @DisplayName("재고 수정 테스트")
    class UpdateStockTest {

        @Test
        @DisplayName("재고를 수정할 수 있다")
        void updateStock() {
            Option option = Option.create(1L, "옵션", Money.of(0L), 100);

            option.updateStock(50);

            assertThat(option.getStock()).isEqualTo(50);
        }

        @Test
        @DisplayName("음수로 재고를 수정하면 예외가 발생한다")
        void updateStockWithNegativeThrowsException() {
            Option option = Option.create(1L, "옵션", Money.of(0L), 100);

            assertThatThrownBy(() -> option.updateStock(-1))
                    .isInstanceOf(CoreException.class);
        }
    }
}

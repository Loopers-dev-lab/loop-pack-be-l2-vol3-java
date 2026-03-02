package com.loopers.domain.common.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QuantityTest {

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 음수이면_예외가_발생한다() {
            // act & assert
            assertThatThrownBy(() -> new Quantity(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 영이면_정상_생성된다() {
            // act
            Quantity quantity = new Quantity(0);

            // assert
            assertThat(quantity.toInt()).isEqualTo(0);
        }

        @Test
        void 양수이면_정상_생성된다() {
            // act
            Quantity quantity = new Quantity(100);

            // assert
            assertThat(quantity.toInt()).isEqualTo(100);
        }

        @Test
        void zero_팩토리로_영을_생성한다() {
            // act
            Quantity quantity = Quantity.zero();

            // assert
            assertThat(quantity.toInt()).isEqualTo(0);
        }
    }

    @DisplayName("더할 때,")
    @Nested
    class 더하기 {

        @Test
        void 두_수량의_합을_반환한다() {
            // arrange
            Quantity a = new Quantity(30);
            Quantity b = new Quantity(20);

            // act
            Quantity result = a.plus(b);

            // assert
            assertThat(result.toInt()).isEqualTo(50);
        }
    }

    @DisplayName("뺄 때,")
    @Nested
    class 빼기 {

        @Test
        void 결과가_음수이면_예외가_발생한다() {
            // arrange
            Quantity a = new Quantity(10);
            Quantity b = new Quantity(20);

            // act & assert
            assertThatThrownBy(() -> a.minus(b))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 유효한_수량이면_차를_반환한다() {
            // arrange
            Quantity a = new Quantity(100);
            Quantity b = new Quantity(30);

            // act
            Quantity result = a.minus(b);

            // assert
            assertThat(result.toInt()).isEqualTo(70);
        }
    }

    @DisplayName("비교할 때,")
    @Nested
    class 비교 {

        @Test
        void 크거나_같으면_true를_반환한다() {
            // arrange
            Quantity a = new Quantity(100);
            Quantity b = new Quantity(50);

            // act & assert
            assertThat(a.isGreaterThanOrEqual(b)).isTrue();
        }

        @Test
        void 작으면_false를_반환한다() {
            // arrange
            Quantity a = new Quantity(10);
            Quantity b = new Quantity(50);

            // act & assert
            assertThat(a.isGreaterThanOrEqual(b)).isFalse();
        }
    }

    @DisplayName("양수 확인할 때,")
    @Nested
    class 양수확인 {

        @Test
        void 양수이면_true를_반환한다() {
            // act & assert
            assertThat(new Quantity(1).isPositive()).isTrue();
        }

        @Test
        void 영이면_false를_반환한다() {
            // act & assert
            assertThat(new Quantity(0).isPositive()).isFalse();
        }
    }

    @DisplayName("동등성을 비교할 때,")
    @Nested
    class 동등성 {

        @Test
        void 같은_수량이면_동등하다() {
            // arrange
            Quantity a = new Quantity(100);
            Quantity b = new Quantity(100);

            // act & assert
            assertThat(a).isEqualTo(b);
        }

        @Test
        void 다른_수량이면_동등하지_않다() {
            // arrange
            Quantity a = new Quantity(100);
            Quantity b = new Quantity(50);

            // act & assert
            assertThat(a).isNotEqualTo(b);
        }
    }
}

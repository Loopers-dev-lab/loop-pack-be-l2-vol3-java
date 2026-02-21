package com.loopers.domain.common.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class MoneyTest {

    @DisplayName("생성할 때,")
    @Nested
    class 생성 {

        @Test
        void 음수_금액이면_예외가_발생한다() {
            // act & assert
            assertThatThrownBy(() -> new Money(-1))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 영원이면_정상_생성된다() {
            // act
            Money money = new Money(0);

            // assert
            assertThat(money.toInt()).isEqualTo(0);
        }

        @Test
        void 양수_금액이면_정상_생성된다() {
            // act
            Money money = new Money(10000);

            // assert
            assertThat(money.toInt()).isEqualTo(10000);
        }

        @Test
        void zero_팩토리로_0원을_생성한다() {
            // act
            Money money = Money.zero();

            // assert
            assertThat(money.toInt()).isEqualTo(0);
        }
    }

    @DisplayName("더할 때,")
    @Nested
    class 더하기 {

        @Test
        void 두_금액의_합을_반환한다() {
            // arrange
            Money a = new Money(10000);
            Money b = new Money(5000);

            // act
            Money result = a.plus(b);

            // assert
            assertThat(result.toInt()).isEqualTo(15000);
        }
    }

    @DisplayName("뺄 때,")
    @Nested
    class 빼기 {

        @Test
        void 결과가_음수이면_예외가_발생한다() {
            // arrange
            Money a = new Money(5000);
            Money b = new Money(10000);

            // act & assert
            assertThatThrownBy(() -> a.minus(b))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void 유효한_금액이면_차를_반환한다() {
            // arrange
            Money a = new Money(10000);
            Money b = new Money(3000);

            // act
            Money result = a.minus(b);

            // assert
            assertThat(result.toInt()).isEqualTo(7000);
        }
    }

    @DisplayName("곱할 때,")
    @Nested
    class 곱하기 {

        @Test
        void 금액에_수량을_곱한_결과를_반환한다() {
            // arrange
            Money unitPrice = new Money(15000);

            // act
            Money result = unitPrice.multiply(3);

            // assert
            assertThat(result.toInt()).isEqualTo(45000);
        }
    }

    @DisplayName("비교할 때,")
    @Nested
    class 비교 {

        @Test
        void 크거나_같으면_true를_반환한다() {
            // arrange
            Money a = new Money(10000);
            Money b = new Money(5000);

            // act & assert
            assertThat(a.isGreaterThanOrEqual(b)).isTrue();
        }

        @Test
        void 같으면_true를_반환한다() {
            // arrange
            Money a = new Money(10000);
            Money b = new Money(10000);

            // act & assert
            assertThat(a.isGreaterThanOrEqual(b)).isTrue();
        }

        @Test
        void 작으면_false를_반환한다() {
            // arrange
            Money a = new Money(5000);
            Money b = new Money(10000);

            // act & assert
            assertThat(a.isGreaterThanOrEqual(b)).isFalse();
        }
    }

    @DisplayName("동등성을 비교할 때,")
    @Nested
    class 동등성 {

        @Test
        void 같은_금액이면_동등하다() {
            // arrange
            Money a = new Money(10000);
            Money b = new Money(10000);

            // act & assert
            assertThat(a).isEqualTo(b);
        }

        @Test
        void 다른_금액이면_동등하지_않다() {
            // arrange
            Money a = new Money(10000);
            Money b = new Money(5000);

            // act & assert
            assertThat(a).isNotEqualTo(b);
        }
    }
}

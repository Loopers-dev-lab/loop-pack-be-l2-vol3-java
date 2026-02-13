package com.loopers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class BirthDateTest {

    @DisplayName("생년월일 객체를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("과거 혹은 현재의 날짜가 주어지면, 정상적으로 생성된다.")
        @Test
        void createBirthDate_whenValidDateProvided() {
            // arrange
            LocalDate validDate = LocalDate.of(1995, 5, 20);

            // act
            BirthDate birthDate = new BirthDate(validDate);

            // assert
            assertThat(birthDate.getDate()).isEqualTo(validDate);
        }

        @DisplayName("날짜가 null이면 예외가 발생한다.")
        @Test
        void createBirthDate_whenDateIsNull() {
            assertThatThrownBy(() -> new BirthDate(null))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("필수 입력값입니다.");
        }

        @DisplayName("날짜가 미래의 날짜이면 예외가 발생한다.")
        @Test
        void createBirthDate_whenDateIsInFuture() {
            // arrange
            LocalDate futureDate = LocalDate.now().plusDays(1);

            // act & assert
            assertThatThrownBy(() -> new BirthDate(futureDate))
                    .isInstanceOf(CoreException.class)
                    .hasMessageContaining("과거 날짜여야 합니다.");
        }
    }

    @DisplayName("날짜를 문자열로 변환할 때, ")
    @Nested
    class Conversion {

        @DisplayName("yyyyMMdd 형식의 문자열을 반환한다.")
        @Test
        void toDateString_returnsFormattedString() {
            // arrange
            BirthDate birthDate = new BirthDate(LocalDate.of(1988, 12, 5));

            // act
            String result = birthDate.toDateString();

            // assert
            assertThat(result).isEqualTo("19881205");
        }
    }
}

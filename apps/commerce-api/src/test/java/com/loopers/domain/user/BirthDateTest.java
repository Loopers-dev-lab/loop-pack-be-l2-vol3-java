package com.loopers.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BirthDateTest {

    @DisplayName("생년월일을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("미래 날짜이면, IllegalArgumentException이 발생한다.")
        @Test
        void create_withFutureDate_shouldFail() {
            // given
            String futureDate = "2999-12-31";

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                new BirthDate(futureDate);
            });
        }

        @DisplayName("형식이 yyyy-MM-dd가 아니면, IllegalArgumentException이 발생한다.")
        @Test
        void create_withInvalidFormat_shouldFail() {
            // given
            String invalidFormat = "1990/01/15";

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                new BirthDate(invalidFormat);
            });
        }

        @DisplayName("올바른 형식(yyyy-MM-dd)의 과거 날짜이면, 정상적으로 생성된다.")
        @Test
        void create_withValidPastDate_shouldSuccess() {
            // given
            String validDate = "1990-01-15";

            // when
            BirthDate birthDate = new BirthDate(validDate);

            // then
            assertThat(birthDate.value()).isEqualTo(validDate);
        }

        @DisplayName("오늘 날짜이면, 정상적으로 생성된다.")
        @Test
        void create_withToday_shouldSuccess() {
            // given
            String today = LocalDate.now().toString();

            // when
            BirthDate birthDate = new BirthDate(today);

            // then
            assertThat(birthDate.value()).isEqualTo(today);
        }

        @DisplayName("null이면, IllegalArgumentException이 발생한다.")
        @Test
        void create_withNull_shouldFail() {
            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                new BirthDate(null);
            });
        }

        @DisplayName("빈 문자열이면, IllegalArgumentException이 발생한다.")
        @Test
        void create_withBlank_shouldFail() {
            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                new BirthDate("");
            });
        }

        @DisplayName("형식이 잘못되면, 예외의 원인(cause)이 DateTimeParseException이다.")
        @Test
        void create_withInvalidFormat_shouldPreserveCause() {
            // given
            String invalidFormat = "1990/01/15";

            // when
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
                new BirthDate(invalidFormat);
            });

            // then
            assertThat(exception.getCause()).isInstanceOf(java.time.format.DateTimeParseException.class);
        }
    }
}

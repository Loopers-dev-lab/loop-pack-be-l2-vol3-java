package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class BirthDateTest {

    @DisplayName("BirthDate를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("yyyy-MM-dd 형식이면, 정상적으로 생성된다.")
        @Test
        void createsBirthDate_whenFormatIsValid() {
            // arrange
            String value = "2026-01-15";

            // act & assert
            assertThatCode(() -> new BirthDate(value))
                    .doesNotThrowAnyException();
        }

        @DisplayName("유효하지 않은 형식이면, INVALID_BIRTH_DATE_FORMAT 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {
                "20260115",
                "2026-13-01",
                "2026-02-29",
                ""
        })
        void throwsInvalidBirthDateFormatException_whenFormatIsInvalid(String value) {
            assertThatThrownBy(() -> new BirthDate(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType())
                            .isEqualTo(ErrorType.INVALID_BIRTH_DATE_FORMAT));
        }
    }

    @DisplayName("getValueWithoutHyphen을 호출할 때,")
    @Nested
    class GetValueWithoutHyphen {

        @DisplayName("하이픈이 제거된 값을 반환한다.")
        @Test
        void returnsValueWithoutHyphen() {
            // arrange
            BirthDate birthDate = new BirthDate("2026-01-15");

            // act
            String result = birthDate.getValueWithoutHyphen();

            // assert
            assertThat(result).isEqualTo("20260115");
        }
    }

    @DisplayName("isValidDateFormat 메서드를 테스트할 때,")
    @Nested
    class IsValidDateFormat {

        @DisplayName("올바른 날짜 형식이면 true를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = {
                "2026-12-31",
                "2024-02-29", // 윤년
                "2026-01-01"
        })
        void returnsTrue_whenDateFormatIsValid(String dateStr) {
            assertThat(BirthDate.isValidDateFormat(dateStr)).isTrue();
        }

        @DisplayName("잘못된 날짜 형식이면 false를 반환한다.")
        @ParameterizedTest
        @ValueSource(strings = {
                "20260115",
                "2026-13-01",
                "2026-01-32",
                "2026-00-15",
                "2026-01-00",
                "2026-02-29",
                "2026-04-31",
                ""
        })
        void returnsFalse_whenDateFormatIsInvalid(String dateStr) {
            assertThat(BirthDate.isValidDateFormat(dateStr)).isFalse();
        }
    }
}

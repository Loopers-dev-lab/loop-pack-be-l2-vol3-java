package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
            String value = "1990-01-15";

            // act & assert
            assertThatCode(() -> new BirthDate(value))
                    .doesNotThrowAnyException();
        }

        @DisplayName("yyyy-MM-dd 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenFormatIsInvalid() {
            // arrange
            String value = "19900115";

            // act & assert
            assertThatThrownBy(() -> new BirthDate(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("월이 12를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenMonthExceeds12() {
            // arrange
            String value = "1990-13-01";

            // act & assert
            assertThatThrownBy(() -> new BirthDate(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("일이 31을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenDayExceeds31() {
            // arrange
            String value = "1990-01-32";

            // act & assert
            assertThatThrownBy(() -> new BirthDate(value))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("getValueWithoutHyphen을 호출할 때,")
    @Nested
    class GetValueWithoutHyphen {

        @DisplayName("하이픈이 제거된 값을 반환한다.")
        @Test
        void returnsValueWithoutHyphen() {
            // arrange
            BirthDate birthDate = new BirthDate("1990-01-15");

            // act
            String result = birthDate.getValueWithoutHyphen();

            // assert
            assertThat(result).isEqualTo("19900115");
        }
    }
}
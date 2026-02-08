package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BirthDateTest {

    @DisplayName("BirthDate를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 생년월일이면 정상 생성된다")
        @Test
        void success_whenValid() {
            BirthDate birthDate = assertDoesNotThrow(() -> new BirthDate(LocalDate.of(1990, 1, 15)));
            assertThat(birthDate.value()).isEqualTo(LocalDate.of(1990, 1, 15));
        }

        @DisplayName("null이면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenNull() {
            assertThatThrownBy(() -> new BirthDate(null))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("미래 날짜면 BAD_REQUEST 예외가 발생한다")
        @Test
        void throwsException_whenFutureDate() {
            assertThatThrownBy(() -> new BirthDate(LocalDate.now().plusDays(1)))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("포맷 변환 시, ")
    @Nested
    class Format {

        @DisplayName("yyyyMMdd 형식으로 변환된다")
        @Test
        void formatsToYyyyMmDd() {
            BirthDate birthDate = new BirthDate(LocalDate.of(1990, 1, 15));
            assertThat(birthDate.toFormattedString()).isEqualTo("19900115");
        }
    }
}

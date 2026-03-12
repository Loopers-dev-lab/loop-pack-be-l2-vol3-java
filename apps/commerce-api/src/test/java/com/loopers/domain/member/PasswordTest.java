package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PasswordTest {

    @DisplayName("Password 생성")
    @Nested
    class Create {

        @DisplayName("null이나 빈 문자열은 허용하지 않는다")
        @ParameterizedTest
        @NullAndEmptySource
        void rejectsNullOrEmpty(String value) {
            // given & when
            CoreException result = assertThrows(CoreException.class, () -> new Password(value));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
        }

        @DisplayName("유효한 비밀번호로 생성할 수 있다")
        @Test
        void validPasswordCreatesSuccessfully() {
            // given
            String value = "ValidPass1!";

            // when
            Password password = new Password(value);

            // then
            assertThat(password.value()).isEqualTo(value);
        }

        @DisplayName("8~16자 범위 내의 비밀번호는 허용된다")
        @ParameterizedTest
        @ValueSource(strings = {"Abcd123!", "Abcd1234!@#$Efgh"})
        void acceptsPasswordsWithinLengthRange(String value) {
            // given & when & then
            assertDoesNotThrow(() -> new Password(value));
        }

        @DisplayName("8~16자 범위를 벗어나면 생성할 수 없다")
        @ParameterizedTest
        @ValueSource(strings = {"Abc123!", "Abcd1234!@#$Efghi"})
        void rejectsPasswordsOutsideLengthRange(String value) {
            // given & when
            CoreException result = assertThrows(CoreException.class, () -> new Password(value));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
        }

        @DisplayName("한글이 포함되면 생성할 수 없다")
        @Test
        void rejectsKoreanCharacters() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () -> new Password("Abcd123한글"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
        }
    }

    @DisplayName("생년월일 포함 여부 검증")
    @Nested
    class ValidateAgainst {

        private static final LocalDate BIRTH_DATE = LocalDate.of(1998, 9, 16);

        @DisplayName("YYYYMMDD 형식의 생년월일이 포함되면 예외가 발생한다")
        @Test
        void rejectsPasswordContainingFullBirthDate() {
            // given
            Password password = new Password("Pass19980916!");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                password.validateAgainst(BIRTH_DATE));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }

        @DisplayName("YYMMDD 형식의 생년월일이 포함되어도 예외가 발생한다")
        @Test
        void rejectsPasswordContainingShortBirthDate() {
            // given
            Password password = new Password("Pass980916!!");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                password.validateAgainst(BIRTH_DATE));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }

        @DisplayName("생년월일이 포함되지 않으면 통과한다")
        @Test
        void passesWhenBirthDateNotIncluded() {
            // given
            Password password = new Password("ValidPass1!");

            // when & then
            assertDoesNotThrow(() -> password.validateAgainst(BIRTH_DATE));
        }

        @DisplayName("birthDate가 null이면 검증을 건너뛴다")
        @Test
        void skipsValidationWhenBirthDateIsNull() {
            // given
            Password password = new Password("ValidPass1!");

            // when & then
            assertDoesNotThrow(() -> password.validateAgainst(null));
        }
    }

    @DisplayName("of 팩토리 메서드")
    @Nested
    class OfFactory {

        @DisplayName("유효한 비밀번호와 생년월일로 생성할 수 있다")
        @Test
        void createsWithValidPasswordAndBirthDate() {
            // given & when & then
            assertDoesNotThrow(() -> Password.of("ValidPass1!", LocalDate.of(1998, 9, 16)));
        }

        @DisplayName("형식이 잘못되면 INVALID_PASSWORD 예외가 발생한다")
        @Test
        void rejectsInvalidFormat() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                Password.of("short", LocalDate.of(1998, 9, 16)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
        }

        @DisplayName("생년월일이 포함된 비밀번호는 거부한다")
        @Test
        void rejectsPasswordWithBirthDate() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                Password.of("Pass19980916!", LocalDate.of(1998, 9, 16)));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }

        @DisplayName("생년월일이 null이면 형식만 검증한다")
        @Test
        void onlyValidatesFormatWhenBirthDateIsNull() {
            // given & when & then
            assertDoesNotThrow(() -> Password.of("ValidPass1!", null));
        }
    }
}

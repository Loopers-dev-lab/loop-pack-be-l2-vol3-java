package com.loopers.domain.member.vo;

import com.loopers.domain.member.service.PasswordEncryptor;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PasswordTest {

    private static final String BIRTH_DATE_STRING = new BirthDate(LocalDate.of(1990, 1, 15)).toFormattedString();
    private static final PasswordEncryptor STUB_ENCRYPTOR = new PasswordEncryptor() {
        @Override
        public String encode(String rawPassword) {
            return "encoded_" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return encodedPassword.equals("encoded_" + rawPassword);
        }
    };

    @DisplayName("Password를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("유효한 값이면 정상 생성된다")
        @Test
        void success() {
            Password password = assertDoesNotThrow(() -> new Password("encodedPassword"));
            assertThat(password.value()).isEqualTo("encodedPassword");
        }

        @DisplayName("null이면 예외가 발생한다")
        @Test
        void throwsException_whenNull() {
            assertThatThrownBy(() -> new Password(null))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("빈 값이면 예외가 발생한다")
        @Test
        void throwsException_whenBlank() {
            assertThatThrownBy(() -> new Password("  "))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }

    @DisplayName("Password.create()로 평문 비밀번호 생성 시, ")
    @Nested
    class CreateFromRaw {

        @DisplayName("비밀번호 길이 검증")
        @Nested
        class LengthValidation {

            @DisplayName("8자 미만이면 예외가 발생한다")
            @Test
            void throwsException_whenLessThan8() {
                assertThatThrownBy(() -> Password.create("Abc123!", BIRTH_DATE_STRING, STUB_ENCRYPTOR))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }

            @DisplayName("16자 초과면 예외가 발생한다")
            @Test
            void throwsException_whenMoreThan16() {
                assertThatThrownBy(() -> Password.create("Abcdefgh12345678!", BIRTH_DATE_STRING, STUB_ENCRYPTOR))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }

            @DisplayName("정확히 8자면 정상 생성된다")
            @Test
            void success_whenExactly8() {
                Password password = assertDoesNotThrow(() -> Password.create("Abcd123!", BIRTH_DATE_STRING, STUB_ENCRYPTOR));
                assertThat(password.value()).isEqualTo("encoded_Abcd123!");
            }

            @DisplayName("정확히 16자면 정상 생성된다")
            @Test
            void success_whenExactly16() {
                Password password = assertDoesNotThrow(() -> Password.create("Abcdefg1234567!@", BIRTH_DATE_STRING, STUB_ENCRYPTOR));
                assertThat(password.value()).isEqualTo("encoded_Abcdefg1234567!@");
            }
        }

        @DisplayName("비밀번호 문자 타입 검증")
        @Nested
        class CharacterTypeValidation {

            @DisplayName("영문이 없으면 예외가 발생한다")
            @Test
            void throwsException_whenNoLetter() {
                assertThatThrownBy(() -> Password.create("12345678!@", BIRTH_DATE_STRING, STUB_ENCRYPTOR))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }

            @DisplayName("숫자가 없으면 예외가 발생한다")
            @Test
            void throwsException_whenNoDigit() {
                assertThatThrownBy(() -> Password.create("Abcdefgh!@", BIRTH_DATE_STRING, STUB_ENCRYPTOR))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }

            @DisplayName("특수문자가 없으면 예외가 발생한다")
            @Test
            void throwsException_whenNoSpecialChar() {
                assertThatThrownBy(() -> Password.create("Abcdefgh12", BIRTH_DATE_STRING, STUB_ENCRYPTOR))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }
        }

        @DisplayName("생년월일 포함 검증")
        @Nested
        class BirthDateContainValidation {

            @DisplayName("생년월일(yyyyMMdd)이 포함되면 예외가 발생한다")
            @Test
            void throwsException_whenContainsBirthDate() {
                assertThatThrownBy(() -> Password.create("Abc19900115!", BIRTH_DATE_STRING, STUB_ENCRYPTOR))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }
        }

        @DisplayName("허용되지 않는 문자 검증")
        @Nested
        class InvalidCharacterValidation {

            @DisplayName("한글이 포함되면 예외가 발생한다")
            @Test
            void throwsException_whenContainsKorean() {
                assertThatThrownBy(() -> Password.create("Abcd1234한글!", BIRTH_DATE_STRING, STUB_ENCRYPTOR))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }

            @DisplayName("공백이 포함되면 예외가 발생한다")
            @Test
            void throwsException_whenContainsSpace() {
                assertThatThrownBy(() -> Password.create("Abcd 1234!", BIRTH_DATE_STRING, STUB_ENCRYPTOR))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            }
        }

        @DisplayName("유효한 비밀번호")
        @Nested
        class ValidPassword {

            @DisplayName("모든 조건을 만족하면 인코딩된 Password가 생성된다")
            @Test
            void success_whenValid() {
                Password password = assertDoesNotThrow(() -> Password.create("Abcd1234!", BIRTH_DATE_STRING, STUB_ENCRYPTOR));
                assertThat(password.value()).isEqualTo("encoded_Abcd1234!");
            }
        }

        @DisplayName("matches()로 평문과 인코딩된 비밀번호를 비교할 수 있다")
        @Test
        void matches_success() {
            Password password = Password.create("Abcd1234!", BIRTH_DATE_STRING, STUB_ENCRYPTOR);
            assertThat(password.matches("Abcd1234!", STUB_ENCRYPTOR)).isTrue();
            assertThat(password.matches("Wrong1234!", STUB_ENCRYPTOR)).isFalse();
        }
    }
}

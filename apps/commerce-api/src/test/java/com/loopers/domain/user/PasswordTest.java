package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Password Value Object 단위 테스트
 *
 * 검증 규칙:
 * - 8~16자
 * - 영문 대소문자, 숫자, 특수문자만 허용
 * - 영문 대문자/소문자/숫자/특수문자 중 3종류 이상 포함
 * - 동일 문자 3회 이상 연속 금지
 * - 연속된 문자/숫자 3자리 이상 금지
 *
 * 교차 검증(생년월일 포함 금지)은 PasswordPolicy에서 별도 테스트
 */
public class PasswordTest {

    @DisplayName("비밀번호를 생성할 때,")
    @Nested
    class Create {

        // ========== 정상 케이스 ==========

        @DisplayName("모든 규칙을 만족하면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenAllRulesSatisfied() {
            // arrange
            String rawPassword = "Hx7!mK2@";

            // act
            Password password = Password.of(rawPassword);

            // assert
            assertThat(password.getValue()).isEqualTo(rawPassword);
        }

        @DisplayName("최소 길이(8자)이면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenMinLength() {
            // arrange
            String rawPassword = "Xz5!qw9@";  // 8자

            // act
            Password password = Password.of(rawPassword);

            // assert
            assertThat(password.getValue()).isEqualTo(rawPassword);
        }

        @DisplayName("최대 길이(16자)이면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenMaxLength() {
            // arrange
            String rawPassword = "Px8!Kd3@Wm7#Rf2$";  // 16자

            // act
            Password password = Password.of(rawPassword);

            // assert
            assertThat(password.getValue()).isEqualTo(rawPassword);
        }

        @DisplayName("다양한 특수문자 조합이면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenVariousSpecialChars() {
            // arrange
            String rawPassword = "Ac1~`[]{}";

            // act & assert
            assertDoesNotThrow(() -> Password.of(rawPassword));
        }

        // ========== 엣지 케이스 ==========

        @DisplayName("null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNull() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of(null);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @DisplayName("빈 문자열이면, 예외가 발생한다.")
        @Test
        void throwsException_whenEmpty() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @DisplayName("7자(최소 미만)이면, 예외가 발생한다.")
        @Test
        void throwsException_whenLessThanMinLength() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abcd12!");  // 7자
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @DisplayName("17자(최대 초과)이면, 예외가 발생한다.")
        @Test
        void throwsException_whenExceedsMaxLength() {
            // arrange
            String rawPassword = "Px8!Kd3@Wm7#Rf2$A";  // 17자

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of(rawPassword);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @DisplayName("영문만 있으면(복잡도 미달), 예외가 발생한다.")
        @Test
        void throwsException_whenOnlyLetters() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abcdefgh");  // 대문자+소문자 = 2종
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @DisplayName("숫자만 있으면(복잡도 미달), 예외가 발생한다.")
        @Test
        void throwsException_whenOnlyDigits() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("12345978");  // 숫자 = 1종
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @DisplayName("특수문자만 있으면(복잡도 미달), 예외가 발생한다.")
        @Test
        void throwsException_whenOnlySpecialChars() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("!@#$%^&*");  // 특수문자 = 1종
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @DisplayName("한글이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsKorean() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abcd123가");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @DisplayName("공백이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsSpace() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abcd 12!");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @DisplayName("동일 문자가 3회 이상 연속되면, 예외가 발생한다.")
        @Test
        void throwsException_whenThreeConsecutiveSameChars() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Aaab123!@");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @DisplayName("연속 숫자 3자리가 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenThreeSequentialDigits() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abzx1234!");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @DisplayName("연속 문자 3자리가 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenThreeSequentialChars() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("xAbcz12!@");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }
    }
}
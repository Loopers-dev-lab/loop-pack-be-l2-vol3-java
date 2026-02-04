package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Password Value Object 단위 테스트
 *
 * 검증 규칙:
 * - 8~16자
 * - 영문 대소문자, 숫자, 특수문자만 허용
 * - 영문 대문자/소문자/숫자/특수문자 중 3종류 이상 포함
 * - 생년월일 포함 금지 (YYYYMMDD, YYMMDD, MMDD)
 * - 동일 문자 3회 이상 연속 금지
 * - 연속된 문자/숫자 3자리 이상 금지
 */
public class PasswordTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1994, 11, 15);

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
            Password password = Password.of(rawPassword, BIRTH_DATE);

            // assert
            assertThat(password.matches(rawPassword)).isTrue();
        }

        @DisplayName("최소 길이(8자)이면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenMinLength() {
            // arrange
            String rawPassword = "Xz5!qw9@";  // 8자

            // act
            Password password = Password.of(rawPassword, BIRTH_DATE);

            // assert
            assertThat(password.matches(rawPassword)).isTrue();
        }

        @DisplayName("최대 길이(16자)이면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenMaxLength() {
            // arrange
            String rawPassword = "Px8!Kd3@Wm7#Rf2$";  // 16자

            // act
            Password password = Password.of(rawPassword, BIRTH_DATE);

            // assert
            assertThat(password.matches(rawPassword)).isTrue();
        }

        @DisplayName("다양한 특수문자 조합이면, 정상적으로 생성된다.")
        @Test
        void createsPassword_whenVariousSpecialChars() {
            // arrange
            String rawPassword = "Ac1~`[]{}";

            // act
            Password password = Password.of(rawPassword, BIRTH_DATE);

            // assert
            assertThat(password.matches(rawPassword)).isTrue();
        }

        // ========== 엣지 케이스 ==========

        @DisplayName("null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNull() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of(null, BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 문자열이면, 예외가 발생한다.")
        @Test
        void throwsException_whenEmpty() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("7자(최소 미만)이면, 예외가 발생한다.")
        @Test
        void throwsException_whenLessThanMinLength() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abcd12!", BIRTH_DATE);  // 7자
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("17자(최대 초과)이면, 예외가 발생한다.")
        @Test
        void throwsException_whenExceedsMaxLength() {
            // arrange
            String rawPassword = "Px8!Kd3@Wm7#Rf2$A";  // 17자

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of(rawPassword, BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("영문만 있으면(복잡도 미달), 예외가 발생한다.")
        @Test
        void throwsException_whenOnlyLetters() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abcdefgh", BIRTH_DATE);  // 대문자+소문자 = 2종
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("숫자만 있으면(복잡도 미달), 예외가 발생한다.")
        @Test
        void throwsException_whenOnlyDigits() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("12345978", BIRTH_DATE);  // 숫자 = 1종
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("특수문자만 있으면(복잡도 미달), 예외가 발생한다.")
        @Test
        void throwsException_whenOnlySpecialChars() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("!@#$%^&*", BIRTH_DATE);  // 특수문자 = 1종
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("한글이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsKorean() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abcd123가", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("공백이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsSpace() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abcd 12!", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일(YYYYMMDD)이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsBirthDateYYYYMMDD() {
            // arrange - birthDate: 1994-11-15
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("A19941115!", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일(MMDD)이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsBirthDateMMDD() {
            // arrange - birthDate: 1994-11-15
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abcd0115!", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일(YYMMDD)이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsBirthDateYYMMDD() {
            // arrange - birthDate: 1994-11-15
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("A941115!a", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("동일 문자가 3회 이상 연속되면, 예외가 발생한다.")
        @Test
        void throwsException_whenThreeConsecutiveSameChars() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Aaab123!@", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("연속 숫자 3자리가 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenThreeSequentialDigits() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abzx1234!", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("연속 문자 3자리가 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenThreeSequentialChars() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("xAbcz12!@", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

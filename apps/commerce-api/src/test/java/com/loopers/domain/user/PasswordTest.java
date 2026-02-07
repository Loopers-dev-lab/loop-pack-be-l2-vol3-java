package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
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
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class PasswordTest {

    @DisplayName("비밀번호를 생성할 때,")
    @Nested
    class Create {

        // ========== 정상 케이스 ==========

        @Test
        void 모든_규칙을_만족하면_정상적으로_생성된다() {
            // arrange
            String rawPassword = "Hx7!mK2@";

            // act
            Password password = Password.of(rawPassword);

            // assert
            assertThat(password.getValue()).isEqualTo(rawPassword);
        }

        @Test
        void 최소_길이_8자이면_정상적으로_생성된다() {
            // arrange
            String rawPassword = "Xz5!qw9@";  // 8자

            // act
            Password password = Password.of(rawPassword);

            // assert
            assertThat(password.getValue()).isEqualTo(rawPassword);
        }

        @Test
        void 최대_길이_16자이면_정상적으로_생성된다() {
            // arrange
            String rawPassword = "Px8!Kd3@Wm7#Rf2$";  // 16자

            // act
            Password password = Password.of(rawPassword);

            // assert
            assertThat(password.getValue()).isEqualTo(rawPassword);
        }

        @Test
        void 다양한_특수문자_조합이면_정상적으로_생성된다() {
            // arrange
            String rawPassword = "Ac1~`[]{}";

            // act & assert
            assertDoesNotThrow(() -> Password.of(rawPassword));
        }

        // ========== 엣지 케이스 ==========

        @Test
        void null이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of(null);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 빈_문자열이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 7자_최소_미만이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abcd12!");  // 7자
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 17자_최대_초과이면_예외가_발생한다() {
            // arrange
            String rawPassword = "Px8!Kd3@Wm7#Rf2$A";  // 17자

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of(rawPassword);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 영문만_있으면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abcdefgh");  // 대문자+소문자 = 2종
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 숫자만_있으면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("12345978");  // 숫자 = 1종
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 특수문자만_있으면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("!@#$%^&*");  // 특수문자 = 1종
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 한글이_포함되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abcd123가");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 공백이_포함되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abcd 12!");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 동일_문자가_3회_이상_연속되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Aaab123!@");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 연속_숫자_3자리가_포함되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("Abzx1234!");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 연속_문자_3자리가_포함되면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                Password.of("xAbcz12!@");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }
    }
}

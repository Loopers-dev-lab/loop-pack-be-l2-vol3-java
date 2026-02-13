package com.loopers.domain.user;

import com.loopers.domain.user.vo.Password;
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

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class PasswordTest {

    @DisplayName("비밀번호를 생성할 때,")
    @Nested
    class Create {

        @Test
        void 모든_규칙을_만족하면_정상적으로_생성된다() {
            Password password = Password.of("Hx7!mK2@");
            assertThat(password.getValue()).isEqualTo("Hx7!mK2@");
        }

        @Test
        void 최소_길이_8자이면_정상적으로_생성된다() {
            Password password = Password.of("Xz5!qw9@");
            assertThat(password.getValue()).isEqualTo("Xz5!qw9@");
        }

        @Test
        void 최대_길이_16자이면_정상적으로_생성된다() {
            Password password = Password.of("Px8!Kd3@Wm7#Rf2$");
            assertThat(password.getValue()).isEqualTo("Px8!Kd3@Wm7#Rf2$");
        }

        @Test
        void 다양한_특수문자_조합이면_정상적으로_생성된다() {
            assertDoesNotThrow(() -> Password.of("Ac1~`[]{}"));
        }

        @Test
        void null이면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> Password.of(null));
            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 빈_문자열이면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> Password.of(""));
            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 길이가_7자_최소_미만이면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> Password.of("Abcd12!"));
            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 길이가_17자_최대_초과이면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> Password.of("Px8!Kd3@Wm7#Rf2$A"));
            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 영문만_있으면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> Password.of("Abcdefgh"));
            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 숫자만_있으면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> Password.of("12345978"));
            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 특수문자만_있으면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> Password.of("!@#$%^&*"));
            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 한글이_포함되면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> Password.of("Abcd123가"));
            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 공백이_포함되면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> Password.of("Abcd 12!"));
            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }
    }
}

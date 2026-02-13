package com.loopers.infrastructure.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BCryptPasswordEncryptor 단위 테스트
 *
 * BCryptPasswordEncoder가 null 입력 시 IllegalArgumentException을 발생시키므로,
 * 어댑터 계층에서 CoreException으로 일관 처리하는지 검증한다.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class BCryptPasswordEncryptorTest {

    private BCryptPasswordEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new BCryptPasswordEncryptor();
    }

    @DisplayName("비밀번호 암호화 시,")
    @Nested
    class Encode {

        @Test
        void 유효한_비밀번호면_암호화된_문자열을_반환한다() {
            String encoded = encryptor.encode("Hx7!mK2@");

            assertThat(encoded).isNotBlank();
            assertThat(encoded).startsWith("$2a$");
        }

        @Test
        void 비밀번호가_null이면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                encryptor.encode(null);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 비밀번호가_빈_문자열이면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                encryptor.encode("   ");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }
    }

    @DisplayName("비밀번호 일치 검증 시,")
    @Nested
    class Matches {

        @Test
        void 일치하는_비밀번호면_true를_반환한다() {
            String encoded = encryptor.encode("Hx7!mK2@");

            assertThat(encryptor.matches("Hx7!mK2@", encoded)).isTrue();
        }

        @Test
        void 불일치하는_비밀번호면_false를_반환한다() {
            String encoded = encryptor.encode("Hx7!mK2@");

            assertThat(encryptor.matches("wrongPw1!", encoded)).isFalse();
        }

        @Test
        void 평문_비밀번호가_null이면_예외가_발생한다() {
            String encoded = encryptor.encode("Hx7!mK2@");

            CoreException exception = assertThrows(CoreException.class, () -> {
                encryptor.matches(null, encoded);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 평문_비밀번호가_빈_문자열이면_예외가_발생한다() {
            String encoded = encryptor.encode("Hx7!mK2@");

            CoreException exception = assertThrows(CoreException.class, () -> {
                encryptor.matches("   ", encoded);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 암호화된_비밀번호가_null이면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                encryptor.matches("Hx7!mK2@", null);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }

        @Test
        void 암호화된_비밀번호가_빈_문자열이면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                encryptor.matches("Hx7!mK2@", "   ");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_PASSWORD);
        }
    }
}

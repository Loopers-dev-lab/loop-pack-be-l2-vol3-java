package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * PasswordPolicy 교차 검증 단위 테스트
 *
 * 검증 규칙:
 * - 비밀번호에 생년월일 포함 금지 (YYYYMMDD, YYMMDD, MMDD)
 */
public class PasswordPolicyTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1994, 11, 15);

    @DisplayName("비밀번호 교차 검증 시,")
    @Nested
    class Validate {

        @DisplayName("생년월일이 포함되지 않으면, 정상 통과한다.")
        @Test
        void passes_whenNoBirthDate() {
            assertDoesNotThrow(() -> PasswordPolicy.validate("Hx7!mK2@", BIRTH_DATE));
        }

        @DisplayName("생년월일(YYYYMMDD)이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsBirthDateYYYYMMDD() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                PasswordPolicy.validate("A19941115!", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }

        @DisplayName("생년월일(YYMMDD)이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsBirthDateYYMMDD() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                PasswordPolicy.validate("A941115!a", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }

        @DisplayName("생년월일(MMDD)이 포함되면, 예외가 발생한다.")
        @Test
        void throwsException_whenContainsBirthDateMMDD() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                PasswordPolicy.validate("Abcd1115!", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }
}

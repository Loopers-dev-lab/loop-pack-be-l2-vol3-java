package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
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
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class PasswordPolicyTest {

    private static final LocalDate BIRTH_DATE = LocalDate.of(1994, 11, 15);

    @DisplayName("비밀번호 교차 검증 시,")
    @Nested
    class Validate {

        @Test
        void 생년월일이_포함되지_않으면_정상_통과한다() {
            assertDoesNotThrow(() -> PasswordPolicy.validate("Hx7!mK2@", BIRTH_DATE));
        }

        @Test
        void 생년월일_YYYYMMDD가_포함되면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                PasswordPolicy.validate("A19941115!", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }

        @Test
        void 생년월일_YYMMDD가_포함되면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                PasswordPolicy.validate("A941115!a", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }

        @Test
        void 생년월일_MMDD가_포함되면_예외가_발생한다() {
            CoreException exception = assertThrows(CoreException.class, () -> {
                PasswordPolicy.validate("Abcd1115!", BIRTH_DATE);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }
}

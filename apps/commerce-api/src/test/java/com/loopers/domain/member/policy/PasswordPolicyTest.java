package com.loopers.domain.member.policy;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    @DisplayName("유효한 비밀번호는 검증을 통과한다")
    @Test
    void validate_withValidPassword_succeeds() {
        assertThatNoException().isThrownBy(() ->
            PasswordPolicy.validate("Password1!", LocalDate.of(1990, 1, 15)));
    }

    @DisplayName("8자 미만 비밀번호는 실패한다")
    @Test
    void validateFormat_withShortPassword_throwsException() {
        assertThatThrownBy(() -> PasswordPolicy.validateFormat("Pass1!"))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("16자 초과 비밀번호는 실패한다")
    @Test
    void validateFormat_withLongPassword_throwsException() {
        assertThatThrownBy(() -> PasswordPolicy.validateFormat("A".repeat(17)))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("null 비밀번호는 실패한다")
    @Test
    void validateFormat_withNull_throwsException() {
        assertThatThrownBy(() -> PasswordPolicy.validateFormat(null))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("생년월일(yyyyMMdd) 포함 비밀번호는 실패한다")
    @Test
    void validate_withBirthDateYYYYMMDD_throwsException() {
        assertThatThrownBy(() ->
            PasswordPolicy.validate("Pass19900115!", LocalDate.of(1990, 1, 15)))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("생년월일(yyMMdd) 포함 비밀번호는 실패한다")
    @Test
    void validate_withBirthDateYYMMDD_throwsException() {
        assertThatThrownBy(() ->
            PasswordPolicy.validate("Pass900115!!", LocalDate.of(1990, 1, 15)))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("금지 문자열이 포함되면 실패한다")
    @Test
    void validateNotContainsSubstrings_withForbidden_throwsException() {
        assertThatThrownBy(() ->
            PasswordPolicy.validateNotContainsSubstrings(
                "hello_forbidden_world",
                List.of("forbidden"),
                "금지 문자열 포함"))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("extractBirthDateStrings는 yyyyMMdd와 yyMMdd를 반환한다")
    @Test
    void extractBirthDateStrings_returnsTwoFormats() {
        List<String> result = PasswordPolicy.extractBirthDateStrings(LocalDate.of(1990, 1, 15));
        org.assertj.core.api.Assertions.assertThat(result).containsExactly("19900115", "900115");
    }
}

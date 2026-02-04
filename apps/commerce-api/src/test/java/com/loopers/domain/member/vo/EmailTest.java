package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailTest {

    @DisplayName("유효한 이메일 형식은 생성에 성공한다.")
    @ParameterizedTest
    @ValueSource(strings = {"test@test.com", "user.name@domain.co.kr", "user+tag@example.org"})
    void create_success(String validEmail) {
        // when
        Email email = new Email(validEmail);

        // then
        assertThat(email.getValue()).isEqualTo(validEmail);
    }

    @DisplayName("잘못된 이메일 형식은 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = {"invalid", "invalid@", "@domain.com", "invalid@domain", "invalid @domain.com"})
    void create_fail_invalid_format(String invalidEmail) {
        assertThatThrownBy(() -> new Email(invalidEmail))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("null 또는 빈 문자열은 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void create_fail_empty(String emptyEmail) {
        assertThatThrownBy(() -> new Email(emptyEmail))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("null은 예외가 발생한다.")
    @Test
    void create_fail_null() {
        assertThatThrownBy(() -> new Email(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }
}

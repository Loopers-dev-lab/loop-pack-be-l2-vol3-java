package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemberIdTest {

    @DisplayName("영문/숫자 조합 10자 이내의 ID는 생성에 성공한다.")
    @ParameterizedTest
    @ValueSource(strings = {"user1", "User123", "abcd1234", "ABCD123456"})
    void create_success(String validId) {
        // when
        MemberId memberId = new MemberId(validId);

        // then
        assertThat(memberId.getValue()).isEqualTo(validId);
    }

    @DisplayName("10자를 초과하는 ID는 예외가 발생한다.")
    @Test
    void create_fail_too_long() {
        // given
        String tooLongId = "abcdefghijk";

        // when & then
        assertThatThrownBy(() -> new MemberId(tooLongId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("영문/숫자 외의 문자가 포함되면 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = {"user@1", "user-1", "user_1", "user 1", "유저1"})
    void create_fail_invalid_chars(String invalidId) {
        assertThatThrownBy(() -> new MemberId(invalidId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("null 또는 빈 문자열은 예외가 발생한다.")
    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void create_fail_empty(String emptyId) {
        assertThatThrownBy(() -> new MemberId(emptyId))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }

    @DisplayName("null은 예외가 발생한다.")
    @Test
    void create_fail_null() {
        assertThatThrownBy(() -> new MemberId(null))
                .isInstanceOf(CoreException.class)
                .extracting("errorType").isEqualTo(ErrorType.BAD_REQUEST);
    }
}

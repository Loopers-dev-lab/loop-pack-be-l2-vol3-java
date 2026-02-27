package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

public class PhoneTest {

    @Nested
    @DisplayName("전화번호 형식 검증")
    class PhoneValidationTest {

        @ParameterizedTest
        @DisplayName("유효한 전화번호 형식")
        @ValueSource(strings = {
                "010-1234-5678",
                "010-0000-0000",
                "010-9999-9999"
        })
        void validPhone(String phone) {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Phone(phone));
        }

        @Test
        @DisplayName("전화번호 형식 오류 - 하이픈 누락")
        void phoneWithoutHyphen() {
            // when & then
            assertThatThrownBy(() -> new Phone("01012345678")).isInstanceOf(CoreException.class)
                    .hasMessage("전화번호는 010-XXXX-XXXX 형식이어야 합니다");
        }

        @ParameterizedTest
        @DisplayName("전화번호 형식 오류 - 잘못된 접두사")
        @ValueSource(strings = {
                "011-1234-5678",
                "016-1234-5678",
                "019-1234-5678",
                "010-123-4567",
                "010-1234-567"
        })
        void invalidPhonePrefix(String phone) {
            // when & then
            assertThatThrownBy(() -> new Phone(phone)).isInstanceOf(CoreException.class);
        }

        @ParameterizedTest
        @DisplayName("전화번호 형식 오류 - 빈 문자열 또는 null")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void emptyOrNullPhone(String phone) {
            // when & then
            assertThatThrownBy(() -> new Phone(phone)).isInstanceOf(CoreException.class)
                    .hasMessage("전화번호는 필수 입력값입니다");
        }

        @ParameterizedTest
        @DisplayName("전화번호 형식 오류 - 완전한 잘못된 형식")
        @ValueSource(strings = {
                "abc-def-ghij",
                "010123456789",
                "010-12-3456",
                "010-12345-678",
                ""
        })
        void completelyInvalidFormat(String phone) {
            // when & then
            assertThatThrownBy(() -> new Phone(phone)).isInstanceOf(CoreException.class);
        }
    }
}

package com.loopers.domain.user.vo;

import com.loopers.domain.user.exception.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

public class EmailTest {

    @Nested
    @DisplayName("이메일 형식 검증 (RFC 5321/5322)")
    class EmailValidationTest {

        @ParameterizedTest
        @DisplayName("유효한 이메일 형식")
        @ValueSource(strings = {
                "user@example.com",
                "user.name@example.com",
                "user+tag@example.com",
                "user-name@example.com",
                "user_name@example.com",
                "user123@example.com",
                "123user@example.com",
                "user@subdomain.example.com",
                "user@example.co.kr",
                "a@b.co"
        })
        void validEmail(String email) {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Email(email));
        }

        @Test
        @DisplayName("이메일 형식 오류 - '@' 누락")
        void emailWithoutAtSign() {
            // when & then
            assertThatThrownBy(() -> new Email("userexample.com"))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("이메일 형식 오류 - 도메인 부분 누락")
        void emailWithoutDomain() {
            // when & then
            assertThatThrownBy(() -> new Email("user@"))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("이메일 형식 오류 - 로컬 부분 누락")
        void emailWithoutLocalPart() {
            // when & then
            assertThatThrownBy(() -> new Email("@example.com"))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("이메일 형식 오류 - 도메인에 '.' 누락")
        void emailWithoutDotInDomain() {
            // when & then
            assertThatThrownBy(() -> new Email("user@examplecom"))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("이메일 형식 오류 - TLD 누락 (마지막 '.' 뒤에 아무것도 없음)")
        void emailWithoutTld() {
            // when & then
            assertThatThrownBy(() -> new Email("user@example."))
                    .isInstanceOf(UserValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("이메일 형식 오류 - 로컬 부분에 허용되지 않는 특수문자 포함")
        @ValueSource(strings = {
                "user name@example.com",
                "user<name>@example.com",
                "user[name]@example.com",
                "user\\name@example.com",
                "user\"name@example.com",
                "user,name@example.com",
                "user;name@example.com",
                "user:name@example.com"
        })
        void emailWithInvalidSpecialCharsInLocalPart(String email) {
            // when & then
            assertThatThrownBy(() -> new Email(email))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("이메일 형식 오류 - 연속된 '.' 포함")
        void emailWithConsecutiveDots() {
            // when & then
            assertThatThrownBy(() -> new Email("user..name@example.com"))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("이메일 형식 오류 - 로컬 부분이 '.'으로 시작")
        void emailStartsWithDot() {
            // when & then
            assertThatThrownBy(() -> new Email(".user@example.com"))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("이메일 형식 오류 - 로컬 부분이 '.'으로 끝남")
        void emailLocalPartEndsWithDot() {
            // when & then
            assertThatThrownBy(() -> new Email("user.@example.com"))
                    .isInstanceOf(UserValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("이메일 형식 오류 - 빈 문자열 또는 null")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void emptyOrNullEmail(String email) {
            // when & then
            assertThatThrownBy(() -> new Email(email))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("이메일 형식 오류 - '@'가 여러 개")
        void emailWithMultipleAtSigns() {
            // when & then
            assertThatThrownBy(() -> new Email("user@@example.com"))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("이메일 형식 오류 - 로컬 부분 64자 초과 (RFC 5321)")
        void emailLocalPartExceeds64Chars() {
            // given
            String longLocalPart = "a".repeat(65) + "@example.com";

            // when & then
            assertThatThrownBy(() -> new Email(longLocalPart))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("이메일 형식 오류 - 전체 길이 254자 초과 (RFC 5321)")
        void emailExceeds254Chars() {
            // given
            String longEmail = "a".repeat(64) + "@" + "b".repeat(189) + ".com";

            // when & then
            assertThatThrownBy(() -> new Email(longEmail))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("로컬 부분 64자 경계값 - 성공")
        void emailLocalPartExactly64Chars() {
            // given
            String exactLocalPart = "a".repeat(64) + "@example.com";

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Email(exactLocalPart));
        }

        @Test
        @DisplayName("전체 길이 254자 경계값 - 성공")
        void emailExactly254Chars() {
            // given
            // 도메인 레이블은 63자까지만 허용 (RFC 1035)
            // 64(로컬) + 1(@) + 63 + 1(.) + 63 + 1(.) + 58 + 1(.) + 2(co) = 254
            String domain = "b".repeat(63) + "." + "c".repeat(63) + "." + "d".repeat(58) + ".co";
            String exactEmail = "a".repeat(64) + "@" + domain;

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new Email(exactEmail));
        }
    }
}

package com.loopers.domain.user.vo;

import com.loopers.domain.user.exception.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

public class UserIdTest {

    @Nested
    @DisplayName("아이디 형식 검증")
    class UserIdValidationTest {

        @ParameterizedTest
        @DisplayName("유효한 아이디 형식")
        @ValueSource(strings = {
                "user",
                "user1",
                "user123",
                "User123",
                "a1234",
                "abcd",
                "user1234567890abcd"
        })
        void validUserId(String userId) {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new UserId(userId));
        }

        @ParameterizedTest
        @DisplayName("아이디 형식 오류 - 빈 문자열 또는 null")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void emptyOrNullUserId(String userId) {
            // when & then
            assertThatThrownBy(() -> new UserId(userId))
                    .isInstanceOf(UserValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("아이디 형식 오류 - 4자 미만")
        @ValueSource(strings = {"a", "ab", "abc"})
        void userIdTooShort(String userId) {
            // when & then
            assertThatThrownBy(() -> new UserId(userId))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("아이디 형식 오류 - 20자 초과")
        void userIdTooLong() {
            // given
            String longUserId = "a".repeat(21);

            // when & then
            assertThatThrownBy(() -> new UserId(longUserId))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("아이디 길이 경계값 - 4자 성공")
        void userIdExactly4Chars() {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new UserId("abcd"));
        }

        @Test
        @DisplayName("아이디 길이 경계값 - 20자 성공")
        void userIdExactly20Chars() {
            // given
            String exactUserId = "a".repeat(20);

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new UserId(exactUserId));
        }

        @ParameterizedTest
        @DisplayName("아이디 형식 오류 - 숫자로 시작")
        @ValueSource(strings = {"1user", "123user", "1234"})
        void userIdStartsWithDigit(String userId) {
            // when & then
            assertThatThrownBy(() -> new UserId(userId))
                    .isInstanceOf(UserValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("아이디 형식 오류 - 특수문자 포함")
        @ValueSource(strings = {
                "user_name",
                "user-name",
                "user.name",
                "user@name",
                "user#name",
                "user$name",
                "user%name",
                "user name",
                "user!name",
                "user+name"
        })
        void userIdWithSpecialChars(String userId) {
            // when & then
            assertThatThrownBy(() -> new UserId(userId))
                    .isInstanceOf(UserValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("아이디 형식 오류 - 한글 포함")
        @ValueSource(strings = {"user한글", "한글user", "유저이름"})
        void userIdWithKorean(String userId) {
            // when & then
            assertThatThrownBy(() -> new UserId(userId))
                    .isInstanceOf(UserValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("아이디 형식 오류 - 예약어 사용")
        @ValueSource(strings = {
                "admin", "ADMIN", "Admin",
                "root", "system", "support",
                "test", "guest", "anonymous"
        })
        void userIdWithReservedWord(String userId) {
            // when & then
            assertThatThrownBy(() -> new UserId(userId))
                    .isInstanceOf(UserValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("예약어 포함하지만 다른 아이디 - 성공")
        @ValueSource(strings = {"admin1", "testuser", "myguest", "root123"})
        void userIdContainsReservedWordButDifferent(String userId) {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new UserId(userId));
        }
    }
}

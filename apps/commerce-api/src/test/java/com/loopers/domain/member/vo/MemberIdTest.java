package com.loopers.domain.member.vo;

import com.loopers.domain.member.exception.MemberValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

public class MemberIdTest {

    @Nested
    @DisplayName("아이디 형식 검증")
    class MemberIdValidationTest {

        @ParameterizedTest
        @DisplayName("유효한 아이디 형식")
        @ValueSource(strings = {
                "member",
                "member1",
                "member123",
                "Member123",
                "a1234",
                "abcd",
                "member1234567890abcd"
        })
        void validMemberId(String memberId) {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new MemberId(memberId));
        }

        @ParameterizedTest
        @DisplayName("아이디 형식 오류 - 빈 문자열 또는 null")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void emptyOrNullMemberId(String memberId) {
            // when & then
            assertThatThrownBy(() -> new MemberId(memberId))
                    .isInstanceOf(MemberValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("아이디 형식 오류 - 4자 미만")
        @ValueSource(strings = {"a", "ab", "abc"})
        void memberIdTooShort(String memberId) {
            // when & then
            assertThatThrownBy(() -> new MemberId(memberId))
                    .isInstanceOf(MemberValidationException.class);
        }

        @Test
        @DisplayName("아이디 형식 오류 - 20자 초과")
        void memberIdTooLong() {
            // given
            String longMemberId = "a".repeat(21);

            // when & then
            assertThatThrownBy(() -> new MemberId(longMemberId))
                    .isInstanceOf(MemberValidationException.class);
        }

        @Test
        @DisplayName("아이디 길이 경계값 - 4자 성공")
        void memberIdExactly4Chars() {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new MemberId("abcd"));
        }

        @Test
        @DisplayName("아이디 길이 경계값 - 20자 성공")
        void memberIdExactly20Chars() {
            // given
            String exactMemberId = "a".repeat(20);

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new MemberId(exactMemberId));
        }

        @ParameterizedTest
        @DisplayName("아이디 형식 오류 - 숫자로 시작")
        @ValueSource(strings = {"1member", "123member", "1234"})
        void memberIdStartsWithDigit(String memberId) {
            // when & then
            assertThatThrownBy(() -> new MemberId(memberId))
                    .isInstanceOf(MemberValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("아이디 형식 오류 - 특수문자 포함")
        @ValueSource(strings = {
                "member_name",
                "member-name",
                "member.name",
                "member@name",
                "member#name",
                "member$name",
                "member%name",
                "member name",
                "member!name",
                "member+name"
        })
        void memberIdWithSpecialChars(String memberId) {
            // when & then
            assertThatThrownBy(() -> new MemberId(memberId))
                    .isInstanceOf(MemberValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("아이디 형식 오류 - 한글 포함")
        @ValueSource(strings = {"member한글", "한글member", "유저이름"})
        void memberIdWithKorean(String memberId) {
            // when & then
            assertThatThrownBy(() -> new MemberId(memberId))
                    .isInstanceOf(MemberValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("아이디 형식 오류 - 예약어 사용")
        @ValueSource(strings = {
                "admin", "ADMIN", "Admin",
                "root", "system", "support",
                "test", "guest", "anonymous"
        })
        void memberIdWithReservedWord(String memberId) {
            // when & then
            assertThatThrownBy(() -> new MemberId(memberId))
                    .isInstanceOf(MemberValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("예약어 포함하지만 다른 아이디 - 성공")
        @ValueSource(strings = {"admin1", "testmember", "myguest", "root123"})
        void memberIdContainsReservedWordButDifferent(String memberId) {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new MemberId(memberId));
        }
    }
}

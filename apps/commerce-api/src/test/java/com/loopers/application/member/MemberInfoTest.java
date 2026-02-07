package com.loopers.application.member;

import com.loopers.domain.member.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MemberInfoTest {

    @DisplayName("이름 마스킹")
    @Nested
    class MaskName {

        @DisplayName("이름의 마지막 글자가 *로 마스킹된다.")
        @Test
        void masksLastCharWithAsterisk() {
            // Arrange
            Member member = new Member("testuser", "encrypted", "홍길동", "19900101", "test@example.com");

            // Act
            MemberInfo info = MemberInfo.fromWithMaskedName(member);

            // Assert
            assertThat(info.name()).isEqualTo("홍길*");
        }

        @DisplayName("영문 이름도 마지막 글자가 *로 마스킹된다.")
        @Test
        void masksLastCharForEnglishName() {
            // Arrange
            Member member = new Member("testuser", "encrypted", "John", "19900101", "test@example.com");

            // Act
            MemberInfo info = MemberInfo.fromWithMaskedName(member);

            // Assert
            assertThat(info.name()).isEqualTo("Joh*");
        }

        @DisplayName("한 글자 이름은 *로 반환된다.")
        @Test
        void returnsSingleAsterisk_whenNameIsSingleChar() {
            // Arrange
            Member member = new Member("testuser", "encrypted", "김", "19900101", "test@example.com");

            // Act
            MemberInfo info = MemberInfo.fromWithMaskedName(member);

            // Assert
            assertThat(info.name()).isEqualTo("*");
        }
    }
}

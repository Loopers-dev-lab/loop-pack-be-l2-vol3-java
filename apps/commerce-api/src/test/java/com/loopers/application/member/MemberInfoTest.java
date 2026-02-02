package com.loopers.application.member;

import com.loopers.domain.member.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class MemberInfoTest {

    @DisplayName("MemberInfo 변환 시,")
    @Nested
    class From {

        @DisplayName("Member 도메인 객체로부터 MemberInfo를 생성하면, password와 birthday는 포함되지 않는다.")
        @Test
        void createsMemberInfo_fromDomain_withoutSensitiveFields() {
            // arrange
            Member member = new Member(1L, "testuser1", "$2a$10$encodedHash", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com");

            // act
            MemberInfo info = MemberInfo.from(member);

            // assert
            assertAll(
                () -> assertThat(info.id()).isEqualTo(1L),
                () -> assertThat(info.loginId()).isEqualTo("testuser1"),
                () -> assertThat(info.name()).isEqualTo("홍길동"),
                () -> assertThat(info.email()).isEqualTo("test@example.com")
            );
        }
    }
}
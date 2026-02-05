package com.loopers.infrastructure.member;

import com.loopers.domain.member.Member;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class MemberEntityTest {

    @DisplayName("MemberEntity 변환 시,")
    @Nested
    class Convert {

        @DisplayName("Member 도메인 객체로부터 MemberEntity를 생성한다.")
        @Test
        void createsMemberEntity_fromDomain() {
            // arrange
            Member member = new Member("testuser1", "Test1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com");
            member.encryptPassword("$2a$10$encodedHash");

            // act
            MemberEntity entity = MemberEntity.from(member);

            // assert
            assertAll(
                () -> assertThat(entity.getLoginId()).isEqualTo("testuser1"),
                () -> assertThat(entity.getPassword()).isEqualTo("$2a$10$encodedHash"),
                () -> assertThat(entity.getName()).isEqualTo("홍길동"),
                () -> assertThat(entity.getBirthday()).isEqualTo(LocalDate.of(1995, 3, 15)),
                () -> assertThat(entity.getEmail()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("MemberEntity를 Member 도메인 객체로 변환한다.")
        @Test
        void convertsToDomain() {
            // arrange
            Member member = new Member("testuser1", "Test1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com");
            member.encryptPassword("$2a$10$encodedHash");
            MemberEntity entity = MemberEntity.from(member);

            // act
            Member domain = entity.toDomain();

            // assert
            assertAll(
                () -> assertThat(domain.getLoginId()).isEqualTo("testuser1"),
                () -> assertThat(domain.getPassword()).isEqualTo("$2a$10$encodedHash"),
                () -> assertThat(domain.getName()).isEqualTo("홍길동"),
                () -> assertThat(domain.getBirthday()).isEqualTo(LocalDate.of(1995, 3, 15)),
                () -> assertThat(domain.getEmail()).isEqualTo("test@example.com")
            );
        }
    }
}
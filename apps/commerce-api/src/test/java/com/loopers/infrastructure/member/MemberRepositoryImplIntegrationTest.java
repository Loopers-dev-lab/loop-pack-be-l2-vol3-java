package com.loopers.infrastructure.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
class MemberRepositoryImplIntegrationTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Member createMember(String loginId, String email) {
        Member member = new Member(loginId, "Test1234!", "홍길동", LocalDate.of(1995, 3, 15), email);
        member.encryptPassword("$2a$10$encodedHash");
        return member;
    }

    @DisplayName("회원을 저장할 때,")
    @Nested
    class Save {

        @DisplayName("정상적으로 저장되고, ID가 부여된다.")
        @Test
        void savesMember_andAssignsId() {
            // arrange
            Member member = createMember("testuser1", "test@example.com");

            // act
            Member saved = memberRepository.save(member);

            // assert
            assertAll(
                () -> assertThat(saved.getId()).isNotNull(),
                () -> assertThat(saved.getLoginId()).isEqualTo("testuser1"),
                () -> assertThat(saved.getPassword()).isEqualTo("$2a$10$encodedHash"),
                () -> assertThat(saved.getName()).isEqualTo("홍길동"),
                () -> assertThat(saved.getBirthday()).isEqualTo(LocalDate.of(1995, 3, 15)),
                () -> assertThat(saved.getEmail()).isEqualTo("test@example.com")
            );
        }
    }

    @DisplayName("로그인 ID 중복을 확인할 때,")
    @Nested
    class ExistsByLoginId {

        @DisplayName("존재하는 loginId이면, true를 반환한다.")
        @Test
        void returnsTrue_whenLoginIdExists() {
            // arrange
            memberRepository.save(createMember("testuser1", "test@example.com"));

            // act
            boolean result = memberRepository.existsByLoginId("testuser1");

            // assert
            assertThat(result).isTrue();
        }

        @DisplayName("존재하지 않는 loginId이면, false를 반환한다.")
        @Test
        void returnsFalse_whenLoginIdDoesNotExist() {
            // act
            boolean result = memberRepository.existsByLoginId("nonexistent");

            // assert
            assertThat(result).isFalse();
        }
    }

    @DisplayName("로그인 ID로 회원을 조회할 때,")
    @Nested
    class FindByLoginId {

        @DisplayName("존재하는 loginId이면, 회원을 반환한다.")
        @Test
        void returnsMember_whenLoginIdExists() {
            // arrange
            memberRepository.save(createMember("testuser1", "test@example.com"));

            // act
            Optional<Member> result = memberRepository.findByLoginId("testuser1");

            // assert
            assertAll(
                () -> assertThat(result).isPresent(),
                () -> assertThat(result.get().getLoginId()).isEqualTo("testuser1"),
                () -> assertThat(result.get().getName()).isEqualTo("홍길동"),
                () -> assertThat(result.get().getBirthday()).isEqualTo(LocalDate.of(1995, 3, 15)),
                () -> assertThat(result.get().getEmail()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("존재하지 않는 loginId이면, 빈 Optional을 반환한다.")
        @Test
        void returnsEmpty_whenLoginIdDoesNotExist() {
            // act
            Optional<Member> result = memberRepository.findByLoginId("nonexistent");

            // assert
            assertThat(result).isEmpty();
        }
    }

    @DisplayName("이메일 중복을 확인할 때,")
    @Nested
    class ExistsByEmail {

        @DisplayName("존재하는 email이면, true를 반환한다.")
        @Test
        void returnsTrue_whenEmailExists() {
            // arrange
            memberRepository.save(createMember("testuser1", "test@example.com"));

            // act
            boolean result = memberRepository.existsByEmail("test@example.com");

            // assert
            assertThat(result).isTrue();
        }

        @DisplayName("존재하지 않는 email이면, false를 반환한다.")
        @Test
        void returnsFalse_whenEmailDoesNotExist() {
            // act
            boolean result = memberRepository.existsByEmail("nonexistent@example.com");

            // assert
            assertThat(result).isFalse();
        }
    }
}
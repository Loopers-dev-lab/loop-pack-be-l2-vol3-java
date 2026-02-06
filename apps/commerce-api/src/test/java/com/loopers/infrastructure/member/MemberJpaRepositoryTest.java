package com.loopers.infrastructure.member;

import com.loopers.domain.member.MemberModel;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
@Import(MySqlTestContainersConfig.class)
class MemberJpaRepositoryTest {

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    private MemberModel createMember(String loginId, String email) {
        return MemberModel.signUp(
            loginId,
            "Password123!",
            "홍길동",
            LocalDate.of(1990, 1, 15),
            email
        );
    }

    @DisplayName("회원 저장")
    @Nested
    class Save {

        @DisplayName("회원을 저장하면 ID가 생성된다")
        @Test
        void saveMember() {
            // arrange
            MemberModel member = createMember("testuser", "test@example.com");

            // act
            MemberModel saved = memberJpaRepository.save(member);

            // assert
            assertThat(saved.getId()).isNotNull();
            assertThat(saved.getLoginId()).isEqualTo("testuser");
        }
    }

    @DisplayName("로그인 ID로 조회")
    @Nested
    class FindByLoginId {

        @DisplayName("존재하는 로그인 ID로 조회하면 회원을 반환한다")
        @Test
        void findByLoginId_whenExists() {
            // arrange
            MemberModel member = createMember("testuser", "test@example.com");
            memberJpaRepository.save(member);

            // act
            Optional<MemberModel> found = memberJpaRepository.findByLoginId("testuser");

            // assert
            assertThat(found).isPresent();
            assertThat(found.get().getLoginId()).isEqualTo("testuser");
        }

        @DisplayName("존재하지 않는 로그인 ID로 조회하면 빈 값을 반환한다")
        @Test
        void findByLoginId_whenNotExists() {
            // act
            Optional<MemberModel> found = memberJpaRepository.findByLoginId("nonexistent");

            // assert
            assertThat(found).isEmpty();
        }
    }

    @DisplayName("이메일로 조회")
    @Nested
    class FindByEmail {

        @DisplayName("존재하는 이메일로 조회하면 회원을 반환한다")
        @Test
        void findByEmail_whenExists() {
            // arrange
            MemberModel member = createMember("testuser", "test@example.com");
            memberJpaRepository.save(member);

            // act
            Optional<MemberModel> found = memberJpaRepository.findByEmail("test@example.com");

            // assert
            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        }

        @DisplayName("존재하지 않는 이메일로 조회하면 빈 값을 반환한다")
        @Test
        void findByEmail_whenNotExists() {
            // act
            Optional<MemberModel> found = memberJpaRepository.findByEmail("nonexistent@example.com");

            // assert
            assertThat(found).isEmpty();
        }
    }

    @DisplayName("로그인 ID 중복 체크")
    @Nested
    class ExistsByLoginId {

        @DisplayName("로그인 ID가 존재하면 true를 반환한다")
        @Test
        void existsByLoginId_whenExists() {
            // arrange
            MemberModel member = createMember("testuser", "test@example.com");
            memberJpaRepository.save(member);

            // act
            boolean exists = memberJpaRepository.existsByLoginId("testuser");

            // assert
            assertThat(exists).isTrue();
        }

        @DisplayName("로그인 ID가 존재하지 않으면 false를 반환한다")
        @Test
        void existsByLoginId_whenNotExists() {
            // act
            boolean exists = memberJpaRepository.existsByLoginId("nonexistent");

            // assert
            assertThat(exists).isFalse();
        }
    }

    @DisplayName("이메일 중복 체크")
    @Nested
    class ExistsByEmail {

        @DisplayName("이메일이 존재하면 true를 반환한다")
        @Test
        void existsByEmail_whenExists() {
            // arrange
            MemberModel member = createMember("testuser", "test@example.com");
            memberJpaRepository.save(member);

            // act
            boolean exists = memberJpaRepository.existsByEmail("test@example.com");

            // assert
            assertThat(exists).isTrue();
        }

        @DisplayName("이메일이 존재하지 않으면 false를 반환한다")
        @Test
        void existsByEmail_whenNotExists() {
            // act
            boolean exists = memberJpaRepository.existsByEmail("nonexistent@example.com");

            // assert
            assertThat(exists).isFalse();
        }
    }
}

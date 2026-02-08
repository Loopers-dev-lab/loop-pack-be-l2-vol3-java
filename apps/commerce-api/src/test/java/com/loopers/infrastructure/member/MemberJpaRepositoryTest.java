package com.loopers.infrastructure.member;

import com.loopers.infrastructure.member.entity.MemberEntity;
import com.loopers.domain.member.MemberModel;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.MemberName;
import com.loopers.domain.member.vo.Password;
import com.loopers.infrastructure.member.repository.MemberJpaRepository;
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

    private MemberEntity createEntity(String loginId, String email) {
        MemberModel model = MemberModel.signUp(
            new LoginId(loginId),
            new Password("Password123!"),
            new MemberName("홍길동"),
            new BirthDate(LocalDate.of(1990, 1, 15)),
            new Email(email)
        );
        return MemberEntity.toEntity(model);
    }

    @DisplayName("회원 저장")
    @Nested
    class Save {

        @DisplayName("회원을 저장하면 ID가 생성된다")
        @Test
        void saveMember() {
            // arrange
            MemberEntity entity = createEntity("testuser", "test@example.com");

            // act
            MemberEntity saved = memberJpaRepository.save(entity);

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
            MemberEntity entity = createEntity("testuser", "test@example.com");
            memberJpaRepository.save(entity);

            // act
            Optional<MemberEntity> found = memberJpaRepository.findByLoginId("testuser");

            // assert
            assertThat(found).isPresent();
            assertThat(found.get().getLoginId()).isEqualTo("testuser");
        }

        @DisplayName("존재하지 않는 로그인 ID로 조회하면 빈 값을 반환한다")
        @Test
        void findByLoginId_whenNotExists() {
            // act
            Optional<MemberEntity> found = memberJpaRepository.findByLoginId("nonexistent");

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
            MemberEntity entity = createEntity("testuser", "test@example.com");
            memberJpaRepository.save(entity);

            // act
            Optional<MemberEntity> found = memberJpaRepository.findByEmail("test@example.com");

            // assert
            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("test@example.com");
        }

        @DisplayName("존재하지 않는 이메일로 조회하면 빈 값을 반환한다")
        @Test
        void findByEmail_whenNotExists() {
            // act
            Optional<MemberEntity> found = memberJpaRepository.findByEmail("nonexistent@example.com");

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
            MemberEntity entity = createEntity("testuser", "test@example.com");
            memberJpaRepository.save(entity);

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
            MemberEntity entity = createEntity("testuser", "test@example.com");
            memberJpaRepository.save(entity);

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

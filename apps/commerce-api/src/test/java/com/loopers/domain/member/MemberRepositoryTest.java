package com.loopers.domain.member;

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
class MemberRepositoryTest {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("loginId로 회원 조회")
    @Nested
    class FindByLoginId {

        @DisplayName("존재하지 않는 loginId면 빈 Optional을 반환한다")
        @Test
        void returnsEmptyForNonExistentLoginId() {
            // given
            String loginId = "nonexistent";

            // when
            Optional<MemberModel> result = memberRepository.findByLoginId(loginId);

            // then
            assertThat(result).isEmpty();
        }

        @DisplayName("존재하는 loginId면 저장된 회원을 반환한다")
        @Test
        void returnsMemberForExistingLoginId() {
            // given
            MemberModel member = new MemberModel(
                new LoginId("kwonmo"), "Test1234!", new MemberName("양권모"),
                LocalDate.of(1998, 9, 16), new Email("kwonmo@example.com"));
            memberRepository.save(member);

            // when
            Optional<MemberModel> result = memberRepository.findByLoginId("kwonmo");

            // then
            assertThat(result).isPresent();
            assertAll(
                () -> assertThat(result.get().loginId().value()).isEqualTo("kwonmo"),
                () -> assertThat(result.get().name().value()).isEqualTo("양권모"),
                () -> assertThat(result.get().birthDate()).isEqualTo(LocalDate.of(1998, 9, 16)),
                () -> assertThat(result.get().email().value()).isEqualTo("kwonmo@example.com")
            );
        }
    }

    @DisplayName("회원 저장")
    @Nested
    class Save {

        @DisplayName("유효한 회원 정보를 저장하면 ID가 생성된다")
        @Test
        void generatesIdOnSave() {
            // given
            MemberModel member = new MemberModel(
                new LoginId("kwonmo"), "Test1234!", new MemberName("양권모"),
                LocalDate.of(1998, 9, 16), new Email("kwonmo@example.com"));

            // when
            MemberModel saved = memberRepository.save(member);

            // then
            assertThat(saved.getId()).isNotNull();
        }
    }
}

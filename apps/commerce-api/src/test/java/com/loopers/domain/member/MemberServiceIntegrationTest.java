package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@SpringBootTest
class MemberServiceIntegrationTest {

    @MockitoSpyBean
    private MemberRepository memberRepository;

    @Autowired
    private MemberService memberService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원 가입")
    @Nested
    class Register {

        @DisplayName("회원 가입시 User 저장이 수행된다")
        @Test
        void register_savesUser_verifiedBySpy() {
            // act
            Member result = memberService.register(
                "user1", "Password1!", "홍길동", "1990-01-15", "test@example.com");

            // assert
            verify(memberRepository).save(any(Member.class));
            assertThat(result.getId()).isNotNull();
        }

        @DisplayName("이미 가입된 ID로 회원가입 시도 시 실패한다")
        @Test
        void register_withDuplicateId_throwsException() {
            // arrange
            memberService.register(
                "user1", "Password1!", "홍길동", "1990-01-15", "test@example.com");

            // act & assert
            assertThatThrownBy(() -> memberService.register(
                "user1", "Password2!", "김철수", "1995-05-20", "other@example.com"))
                .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("내 정보 조회")
    @Nested
    class FindByLoginId {

        @DisplayName("해당 ID의 회원이 존재할 경우 회원 정보가 반환된다")
        @Test
        void findByLoginId_whenExists_returnsMember() {
            // arrange
            memberService.register(
                "user1", "Password1!", "홍길동", "1990-01-15", "test@example.com");

            // act
            Optional<Member> result = memberService.findByLoginId("user1");

            // assert
            assertThat(result).isPresent();
            assertThat(result.get().getLoginId().value()).isEqualTo("user1");
        }

        @DisplayName("해당 ID의 회원이 존재하지 않을 경우 null이 반환된다")
        @Test
        void findByLoginId_whenNotExists_returnsEmpty() {
            // act
            Optional<Member> result = memberService.findByLoginId("nobody");

            // assert
            assertThat(result).isEmpty();
        }
    }
}

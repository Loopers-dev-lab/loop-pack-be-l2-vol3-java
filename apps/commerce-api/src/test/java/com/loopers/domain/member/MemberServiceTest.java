package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberServiceTest {

    private MemberService memberService;
    private FakeMemberReader fakeMemberReader;
    private FakeMemberRepository fakeMemberRepository;
    private StubPasswordEncoder stubPasswordEncoder;

    @BeforeEach
    void setUp() {
        fakeMemberReader = new FakeMemberReader();
        fakeMemberRepository = new FakeMemberRepository();
        stubPasswordEncoder = new StubPasswordEncoder();
        memberService = new MemberService(fakeMemberReader, fakeMemberRepository, stubPasswordEncoder);
    }

    @DisplayName("회원가입 시, ")
    @Nested
    class Register {

        @DisplayName("이미 존재하는 로그인ID로 가입하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdAlreadyExists() {
            // Arrange
            String existingLoginId = "existingUser";
            fakeMemberReader.addExistingLoginId(existingLoginId);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                memberService.register(
                    existingLoginId, "Test1234!", "홍길동",
                    LocalDate.of(1990, 1, 15), "test@example.com"
                );
            });

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 정보로 가입하면, 회원이 저장된다.")
        @Test
        void savesMember_whenAllFieldsAreValid() {
            // Arrange
            String loginId = "newUser";
            String password = "Test1234!";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String email = "test@example.com";

            // Act
            Member member = memberService.register(loginId, password, name, birthDate, email);

            // Assert
            assertAll(
                () -> assertThat(member.getLoginId()).isEqualTo(loginId),
                () -> assertThat(member.getPassword()).isEqualTo("encoded_" + password),
                () -> assertThat(member.getName()).isEqualTo(name),
                () -> assertThat(member.getBirthDate()).isEqualTo(birthDate),
                () -> assertThat(member.getEmail()).isEqualTo(email)
            );
        }
    }

    // Fake 구현체
    static class FakeMemberReader implements MemberReader {
        private final Map<String, Boolean> existingLoginIds = new HashMap<>();

        void addExistingLoginId(String loginId) {
            existingLoginIds.put(loginId, true);
        }

        @Override
        public boolean existsByLoginId(String loginId) {
            return existingLoginIds.containsKey(loginId);
        }
    }

    static class FakeMemberRepository implements MemberRepository {
        private final Map<Long, Member> members = new HashMap<>();
        private long idSequence = 1L;

        @Override
        public Member save(Member member) {
            members.put(idSequence++, member);
            return member;
        }
    }

    static class StubPasswordEncoder implements PasswordEncoder {
        @Override
        public String encode(String rawPassword) {
            return "encoded_" + rawPassword;
        }

        @Override
        public boolean matches(String rawPassword, String encodedPassword) {
            return encodedPassword.equals("encoded_" + rawPassword);
        }
    }
}

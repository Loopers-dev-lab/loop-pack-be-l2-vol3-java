package com.loopers.domain.member;

import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.model.MemberCommand;
import com.loopers.domain.member.service.MemberService;
import com.loopers.domain.member.service.PasswordEncryptor;
import com.loopers.infrastructure.member.entity.MemberEntity;
import com.loopers.infrastructure.member.repository.MemberJpaRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
@Transactional
@Import(MySqlTestContainersConfig.class)
class MemberServiceIntegrationTest {

    @Autowired
    private MemberService memberService;

    @Autowired
    private MemberJpaRepository memberJpaRepository;

    @Autowired
    private PasswordEncryptor passwordEncryptor;

    @BeforeEach
    void setUp() {
        memberJpaRepository.deleteAll();
    }

    private MemberCommand.SignUp signUpCommand(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        return new MemberCommand.SignUp(loginId, rawPassword, name, birthDate, email);
    }

    @DisplayName("회원가입 통합 테스트")
    @Nested
    class SignUp {

        @DisplayName("유효한 정보로 회원가입하면 DB에 저장된다")
        @Test
        void signUp_success() {
            // arrange
            MemberCommand.SignUp command = signUpCommand(
                "testuser", "Password123!", "홍길동",
                LocalDate.of(1990, 1, 15), "test@example.com"
            );

            // act
            assertDoesNotThrow(() -> memberService.addMember(command));

            // assert - DB에 실제로 저장되었는지 확인
            MemberEntity saved = memberJpaRepository.findByLoginId("testuser").orElseThrow();
            assertThat(saved.getLoginId()).isEqualTo("testuser");
            assertThat(saved.getName()).isEqualTo("홍길동");
            assertThat(saved.getEmail()).isEqualTo("test@example.com");
            assertThat(passwordEncryptor.matches("Password123!", saved.getPassword())).isTrue();
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면 예외가 발생한다")
        @Test
        void signUp_duplicateLoginId() {
            // arrange - 먼저 회원 생성
            memberService.addMember(signUpCommand(
                "existinguser", "Password123!", "홍길동",
                LocalDate.of(1990, 1, 15), "first@example.com"
            ));

            // act & assert
            assertThatThrownBy(() -> memberService.addMember(signUpCommand(
                "existinguser", "Password456!", "김철수",
                LocalDate.of(1985, 5, 20), "second@example.com"
            )))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @DisplayName("이미 존재하는 이메일로 가입하면 예외가 발생한다")
        @Test
        void signUp_duplicateEmail() {
            // arrange - 먼저 회원 생성
            memberService.addMember(signUpCommand(
                "firstuser", "Password123!", "홍길동",
                LocalDate.of(1990, 1, 15), "duplicate@example.com"
            ));

            // act & assert
            assertThatThrownBy(() -> memberService.addMember(signUpCommand(
                "seconduser", "Password456!", "김철수",
                LocalDate.of(1985, 5, 20), "duplicate@example.com"
            )))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }
    }

    @DisplayName("회원 조회 및 인증 통합 테스트")
    @Nested
    class FindMember {

        @DisplayName("올바른 로그인 정보로 인증하면 회원 정보를 반환한다")
        @Test
        void findMember_success() {
            // arrange - 회원 생성
            String rawPassword = "Password123!";
            memberService.addMember(signUpCommand(
                "testuser", rawPassword, "홍길동",
                LocalDate.of(1990, 1, 15), "test@example.com"
            ));

            // act
            Member result = memberService.findMember("testuser", rawPassword);

            // assert
            assertThat(result.getLoginId().value()).isEqualTo("testuser");
            assertThat(result.getName().value()).isEqualTo("홍길동");
            assertThat(result.getEmail().value()).isEqualTo("test@example.com");
        }

        @DisplayName("존재하지 않는 로그인 ID로 인증하면 예외가 발생한다")
        @Test
        void findMember_notFound() {
            assertThatThrownBy(() -> memberService.findMember("nonexistent", "Password123!"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("비밀번호가 틀리면 예외가 발생한다")
        @Test
        void findMember_wrongPassword() {
            // arrange - 회원 생성
            memberService.addMember(signUpCommand(
                "testuser", "Correct1234!", "홍길동",
                LocalDate.of(1990, 1, 15), "test@example.com"
            ));

            // act & assert
            assertThatThrownBy(() -> memberService.findMember("testuser", "Wrong12345!"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }
    }

    @DisplayName("비밀번호 변경 통합 테스트")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 현재 비밀번호로 변경하면 DB에 반영된다")
        @Test
        void changePassword_success() {
            // arrange - 회원 생성
            String currentPassword = "OldPassword123!";
            String newPassword = "NewPassword456!";

            memberService.addMember(signUpCommand(
                "testuser", currentPassword, "홍길동",
                LocalDate.of(1990, 1, 15), "test@example.com"
            ));

            MemberCommand.ChangePassword command = new MemberCommand.ChangePassword(
                "testuser", currentPassword, currentPassword, newPassword
            );

            // act
            assertDoesNotThrow(() -> memberService.changePassword(command));

            // assert - DB에서 새 비밀번호 확인
            MemberEntity updated = memberJpaRepository.findByLoginId("testuser").orElseThrow();
            assertThat(passwordEncryptor.matches(newPassword, updated.getPassword())).isTrue();
            assertThat(passwordEncryptor.matches(currentPassword, updated.getPassword())).isFalse();
        }

        @DisplayName("인증 비밀번호와 현재 비밀번호가 일치하지 않으면 BAD_REQUEST 예외가 발생한다")
        @Test
        void changePassword_wrongCurrentPassword() {
            // arrange - 회원 생성
            String actualPassword = "Correct1234!";
            memberService.addMember(signUpCommand(
                "testuser", actualPassword, "홍길동",
                LocalDate.of(1990, 1, 15), "test@example.com"
            ));

            MemberCommand.ChangePassword command = new MemberCommand.ChangePassword(
                "testuser", actualPassword, "Wrong12345!", "NewPass1234!"
            );

            // act & assert
            assertThatThrownBy(() -> memberService.changePassword(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("loginPassword와 currentPassword가 모두 틀리면 UNAUTHORIZED 예외가 발생한다")
        @Test
        void changePassword_bothPasswordsWrongButMatching() {
            // arrange
            String actualPassword = "Correct1234!";
            memberService.addMember(signUpCommand("testuser", actualPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com"));
            MemberCommand.ChangePassword command = new MemberCommand.ChangePassword(
                "testuser", "Wrong12345!", "Wrong12345!", "NewPass1234!"
            );

            // act & assert
            assertThatThrownBy(() -> memberService.changePassword(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 예외가 발생한다")
        @Test
        void changePassword_samePassword() {
            // arrange - 회원 생성
            String samePassword = "SamePass1234!";
            memberService.addMember(signUpCommand(
                "testuser", samePassword, "홍길동",
                LocalDate.of(1990, 1, 15), "test@example.com"
            ));

            MemberCommand.ChangePassword command = new MemberCommand.ChangePassword(
                "testuser", samePassword, samePassword, samePassword
            );

            // act & assert
            assertThatThrownBy(() -> memberService.changePassword(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}

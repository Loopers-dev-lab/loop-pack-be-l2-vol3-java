package com.loopers.domain.member;

import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.LoginId;
import com.loopers.domain.member.vo.MemberName;
import com.loopers.domain.member.vo.Password;
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
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        memberJpaRepository.deleteAll();
    }

    @DisplayName("회원가입 통합 테스트")
    @Nested
    class SignUp {

        @DisplayName("유효한 정보로 회원가입하면 DB에 저장된다")
        @Test
        void signUp_success() {
            // arrange
            LoginId loginId = new LoginId("testuser");
            Password password = new Password(passwordEncoder.encode("Password123!"));
            MemberName name = new MemberName("홍길동");
            BirthDate birthDate = new BirthDate(LocalDate.of(1990, 1, 15));
            Email email = new Email("test@example.com");

            // act
            assertDoesNotThrow(() -> memberService.addMember(loginId, password, name, birthDate, email));

            // assert - DB에 실제로 저장되었는지 확인
            MemberEntity saved = memberJpaRepository.findByLoginId("testuser").orElseThrow();
            assertThat(saved.getLoginId()).isEqualTo("testuser");
            assertThat(saved.getName()).isEqualTo("홍길동");
            assertThat(saved.getEmail()).isEqualTo("test@example.com");
            assertThat(passwordEncoder.matches("Password123!", saved.getPassword())).isTrue();
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면 예외가 발생한다")
        @Test
        void signUp_duplicateLoginId() {
            // arrange - 먼저 회원 생성
            memberService.addMember(
                new LoginId("existinguser"),
                new Password(passwordEncoder.encode("Password123!")),
                new MemberName("홍길동"),
                new BirthDate(LocalDate.of(1990, 1, 15)),
                new Email("first@example.com")
            );

            // act & assert
            assertThatThrownBy(() -> memberService.addMember(
                new LoginId("existinguser"),
                new Password(passwordEncoder.encode("Password456!")),
                new MemberName("김철수"),
                new BirthDate(LocalDate.of(1985, 5, 20)),
                new Email("second@example.com")
            ))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @DisplayName("이미 존재하는 이메일로 가입하면 예외가 발생한다")
        @Test
        void signUp_duplicateEmail() {
            // arrange - 먼저 회원 생성
            memberService.addMember(
                new LoginId("firstuser"),
                new Password(passwordEncoder.encode("Password123!")),
                new MemberName("홍길동"),
                new BirthDate(LocalDate.of(1990, 1, 15)),
                new Email("duplicate@example.com")
            );

            // act & assert
            assertThatThrownBy(() -> memberService.addMember(
                new LoginId("seconduser"),
                new Password(passwordEncoder.encode("Password456!")),
                new MemberName("김철수"),
                new BirthDate(LocalDate.of(1985, 5, 20)),
                new Email("duplicate@example.com")
            ))
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
            memberService.addMember(
                new LoginId("testuser"),
                new Password(passwordEncoder.encode(rawPassword)),
                new MemberName("홍길동"),
                new BirthDate(LocalDate.of(1990, 1, 15)),
                new Email("test@example.com")
            );

            // act
            MemberModel result = memberService.findMember("testuser", rawPassword);

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
            memberService.addMember(
                new LoginId("testuser"),
                new Password(passwordEncoder.encode("Correct1234!")),
                new MemberName("홍길동"),
                new BirthDate(LocalDate.of(1990, 1, 15)),
                new Email("test@example.com")
            );

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
            String newRawPassword = "NewPassword456!";

            memberService.addMember(
                new LoginId("testuser"),
                new Password(passwordEncoder.encode(currentPassword)),
                new MemberName("홍길동"),
                new BirthDate(LocalDate.of(1990, 1, 15)),
                new Email("test@example.com")
            );

            Password newPassword = new Password(passwordEncoder.encode(newRawPassword));

            // act
            assertDoesNotThrow(() -> memberService.updatePassword("testuser", newPassword));

            // assert - DB에서 새 비밀번호 확인
            MemberEntity updated = memberJpaRepository.findByLoginId("testuser").orElseThrow();
            assertThat(passwordEncoder.matches(newRawPassword, updated.getPassword())).isTrue();
            assertThat(passwordEncoder.matches(currentPassword, updated.getPassword())).isFalse();
        }

        @DisplayName("현재 비밀번호 검증 시 틀리면 예외가 발생한다")
        @Test
        void verifyPassword_wrongCurrentPassword() {
            // arrange - 회원 생성
            String actualPassword = "Correct1234!";
            memberService.addMember(
                new LoginId("testuser"),
                new Password(passwordEncoder.encode(actualPassword)),
                new MemberName("홍길동"),
                new BirthDate(LocalDate.of(1990, 1, 15)),
                new Email("test@example.com")
            );

            MemberModel member = memberService.findMember("testuser", actualPassword);

            // act & assert
            assertThatThrownBy(() -> memberService.verifyPassword(member, "Wrong12345!"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}

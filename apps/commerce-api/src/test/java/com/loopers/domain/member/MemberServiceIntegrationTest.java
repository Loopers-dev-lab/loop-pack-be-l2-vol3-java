package com.loopers.domain.member;

import com.loopers.application.member.dto.AddMemberReqDto;
import com.loopers.application.member.dto.FindMemberResDto;
import com.loopers.application.member.dto.PutMemberPasswordReqDto;
import com.loopers.infrastructure.member.MemberJpaRepository;
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
            AddMemberReqDto command = new AddMemberReqDto(
                "testuser",
                "Password123!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );

            // act
            assertDoesNotThrow(() -> memberService.addMember(command));

            // assert - DB에 실제로 저장되었는지 확인
            MemberModel saved = memberJpaRepository.findByLoginId("testuser").orElseThrow();
            assertThat(saved.getLoginId()).isEqualTo("testuser");
            assertThat(saved.getName()).isEqualTo("홍길동");
            assertThat(saved.getEmail()).isEqualTo("test@example.com");
            assertThat(passwordEncoder.matches("Password123!", saved.getPassword())).isTrue();
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면 예외가 발생한다")
        @Test
        void signUp_duplicateLoginId() {
            // arrange - 먼저 회원 생성
            AddMemberReqDto firstCommand = new AddMemberReqDto(
                "existinguser",
                "Password123!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "first@example.com"
            );
            memberService.addMember(firstCommand);

            // 같은 loginId로 다시 가입 시도
            AddMemberReqDto duplicateCommand = new AddMemberReqDto(
                "existinguser",
                "Password456!",
                "김철수",
                LocalDate.of(1985, 5, 20),
                "second@example.com"
            );

            // act & assert
            assertThatThrownBy(() -> memberService.addMember(duplicateCommand))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @DisplayName("이미 존재하는 이메일로 가입하면 예외가 발생한다")
        @Test
        void signUp_duplicateEmail() {
            // arrange - 먼저 회원 생성
            AddMemberReqDto firstCommand = new AddMemberReqDto(
                "firstuser",
                "Password123!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "duplicate@example.com"
            );
            memberService.addMember(firstCommand);

            // 같은 email로 다시 가입 시도
            AddMemberReqDto duplicateCommand = new AddMemberReqDto(
                "seconduser",
                "Password456!",
                "김철수",
                LocalDate.of(1985, 5, 20),
                "duplicate@example.com"
            );

            // act & assert
            assertThatThrownBy(() -> memberService.addMember(duplicateCommand))
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
            String password = "Password123!";
            AddMemberReqDto command = new AddMemberReqDto(
                "testuser",
                password,
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            memberService.addMember(command);

            // act
            FindMemberResDto result = memberService.findMember("testuser", password);

            // assert
            assertThat(result.loginId()).isEqualTo("testuser");
            assertThat(result.name()).isEqualTo("홍길동");
            assertThat(result.email()).isEqualTo("test@example.com");
        }

        @DisplayName("존재하지 않는 로그인 ID로 인증하면 예외가 발생한다")
        @Test
        void findMember_notFound() {
            // act & assert
            assertThatThrownBy(() -> memberService.findMember("nonexistent", "Password123!"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("비밀번호가 틀리면 예외가 발생한다")
        @Test
        void findMember_wrongPassword() {
            // arrange - 회원 생성
            AddMemberReqDto command = new AddMemberReqDto(
                "testuser",
                "Correct1234!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            memberService.addMember(command);

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

            AddMemberReqDto signUpCommand = new AddMemberReqDto(
                "testuser",
                currentPassword,
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            memberService.addMember(signUpCommand);

            PutMemberPasswordReqDto changeCommand = new PutMemberPasswordReqDto(
                "testuser",
                currentPassword,
                currentPassword,
                newPassword
            );

            // act
            assertDoesNotThrow(() -> memberService.putPassword(changeCommand));

            // assert - DB에서 새 비밀번호 확인
            MemberModel updated = memberJpaRepository.findByLoginId("testuser").orElseThrow();
            assertThat(passwordEncoder.matches(newPassword, updated.getPassword())).isTrue();
            assertThat(passwordEncoder.matches(currentPassword, updated.getPassword())).isFalse();
        }

        @DisplayName("현재 비밀번호가 틀리면 예외가 발생한다")
        @Test
        void changePassword_wrongCurrentPassword() {
            // arrange - 회원 생성
            String actualPassword = "Correct1234!";
            AddMemberReqDto signUpCommand = new AddMemberReqDto(
                "testuser",
                actualPassword,
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            memberService.addMember(signUpCommand);

            // 헤더 인증은 통과, 현재 비밀번호는 틀림
            PutMemberPasswordReqDto changeCommand = new PutMemberPasswordReqDto(
                "testuser",
                actualPassword,
                "Wrong12345!",
                "NewPass1234!"
            );

            // act & assert
            assertThatThrownBy(() -> memberService.putPassword(changeCommand))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 예외가 발생한다")
        @Test
        void changePassword_samePassword() {
            // arrange - 회원 생성
            String samePassword = "SamePassword123!";

            AddMemberReqDto signUpCommand = new AddMemberReqDto(
                "testuser",
                samePassword,
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            );
            memberService.addMember(signUpCommand);

            PutMemberPasswordReqDto changeCommand = new PutMemberPasswordReqDto(
                "testuser",
                samePassword,
                samePassword,
                samePassword
            );

            // act & assert
            assertThatThrownBy(() -> memberService.putPassword(changeCommand))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}

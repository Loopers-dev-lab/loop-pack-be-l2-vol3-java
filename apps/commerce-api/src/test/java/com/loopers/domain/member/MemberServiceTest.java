package com.loopers.domain.member;

import com.loopers.application.member.dto.AddMemberReqDto;
import com.loopers.application.member.dto.FindMemberResDto;
import com.loopers.application.member.dto.PutMemberPasswordReqDto;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @InjectMocks
    private MemberService memberService;

    @Mock
    private MemberRepository memberRepository;

    @Spy
    private PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @DisplayName("회원가입")
    @Nested
    class SignUp {

        @DisplayName("유효한 정보로 회원가입하면 회원이 생성된다")
        @Test
        void signUp_success() {
            // arrange
            AddMemberReqDto command = new AddMemberReqDto(
                "testuser", "Password123!", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com"
            );

            given(memberRepository.existsByLoginId(command.loginId())).willReturn(false);
            given(memberRepository.existsByEmail(command.email())).willReturn(false);

            // act
            assertDoesNotThrow(() -> memberService.addMember(command));

            // assert
            verify(memberRepository).save(any(MemberModel.class));
        }

        @DisplayName("이미 존재하는 로그인 ID로 가입하면 예외가 발생한다")
        @Test
        void signUp_duplicateLoginId() {
            // arrange
            AddMemberReqDto command = new AddMemberReqDto(
                "existinguser", "Password123!", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com"
            );
            given(memberRepository.existsByLoginId(command.loginId())).willReturn(true);

            // act & assert
            assertThatThrownBy(() -> memberService.addMember(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @DisplayName("이미 존재하는 이메일로 가입하면 예외가 발생한다")
        @Test
        void signUp_duplicateEmail() {
            // arrange
            AddMemberReqDto command = new AddMemberReqDto(
                "newuser", "Password123!", "홍길동", LocalDate.of(1990, 1, 15), "existing@example.com"
            );
            given(memberRepository.existsByLoginId(anyString())).willReturn(false);
            given(memberRepository.existsByEmail(command.email())).willReturn(true);

            // act & assert
            assertThatThrownBy(() -> memberService.addMember(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }

        @DisplayName("비밀번호는 암호화되어 저장된다")
        @Test
        void signUp_passwordEncrypted() {
            // arrange
            String rawPassword = "Password123!";
            AddMemberReqDto command = new AddMemberReqDto(
                "testuser", rawPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com"
            );
            given(memberRepository.existsByLoginId(anyString())).willReturn(false);
            given(memberRepository.existsByEmail(anyString())).willReturn(false);
            given(memberRepository.save(any(MemberModel.class))).willAnswer(invocation -> {
                MemberModel saved = invocation.getArgument(0);
                assertThat(saved.getPassword()).isNotEqualTo(rawPassword);
                assertThat(passwordEncoder.matches(rawPassword, saved.getPassword())).isTrue();
                return saved;
            });

            // act
            memberService.addMember(command);

            // assert
            verify(memberRepository).save(any(MemberModel.class));
        }
    }


    @DisplayName("회원 조회 및 인증")
    @Nested
    class FindMember {

        @DisplayName("올바른 로그인 정보로 인증하면 회원 정보를 반환한다")
        @Test
        void findMember_success() {
            // arrange
            String password = "Password123!";
            String encodedPassword = passwordEncoder.encode(password);
            MemberModel member = MemberModel.signUp("testuser", encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            given(memberRepository.findByLoginId("testuser")).willReturn(Optional.of(member));

            // act
            FindMemberResDto result = memberService.findMember("testuser", password);

            // assert
            assertThat(result.loginId()).isEqualTo("testuser");
            assertThat(result.name()).isEqualTo("홍길동");
        }

        @DisplayName("존재하지 않는 로그인 ID로 인증하면 예외가 발생한다")
        @Test
        void findMember_notFound() {
            // arrange
            given(memberRepository.findByLoginId("nonexistent")).willReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> memberService.findMember("nonexistent", "Password123!"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @DisplayName("비밀번호가 틀리면 예외가 발생한다")
        @Test
        void findMember_wrongPassword() {
            // arrange
            String encodedPassword = passwordEncoder.encode("Correct1234!");
            MemberModel member = MemberModel.signUp("testuser", encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            given(memberRepository.findByLoginId("testuser")).willReturn(Optional.of(member));

            // act & assert
            assertThatThrownBy(() -> memberService.findMember("testuser", "Wrong12345!"))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }
    }

    @DisplayName("비밀번호 변경")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 현재 비밀번호로 변경하면 성공한다")
        @Test
        void changePassword_success() {
            // arrange
            String currentPassword = "OldPassword123!";
            String newPassword = "NewPassword456!";
            String encodedCurrentPassword = passwordEncoder.encode(currentPassword);

            MemberModel member = MemberModel.signUp("testuser", encodedCurrentPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            given(memberRepository.findByLoginId("testuser")).willReturn(Optional.of(member));

            PutMemberPasswordReqDto command = new PutMemberPasswordReqDto("testuser", currentPassword, currentPassword, newPassword);

            // act
            assertDoesNotThrow(() -> memberService.putPassword(command));

            // assert
            assertThat(passwordEncoder.matches(newPassword, member.getPassword())).isTrue();
        }

        @DisplayName("현재 비밀번호가 틀리면 예외가 발생한다")
        @Test
        void changePassword_wrongCurrentPassword() {
            // arrange
            String actualPassword = "CorrectPassword123!";
            String wrongCurrentPassword = "WrongPassword123!";
            String encodedPassword = passwordEncoder.encode(actualPassword);

            MemberModel member = MemberModel.signUp("testuser", encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            given(memberRepository.findByLoginId("testuser")).willReturn(Optional.of(member));

            // 헤더 인증은 통과, 현재 비밀번호는 틀림
            PutMemberPasswordReqDto command = new PutMemberPasswordReqDto("testuser", actualPassword, wrongCurrentPassword, "NewPassword456!");

            // act & assert
            assertThatThrownBy(() -> memberService.putPassword(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 같으면 예외가 발생한다")
        @Test
        void changePassword_samePassword() {
            // arrange
            String currentPassword = "SamePassword123!";
            String encodedCurrentPassword = passwordEncoder.encode(currentPassword);

            MemberModel member = MemberModel.signUp("testuser", encodedCurrentPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            given(memberRepository.findByLoginId("testuser")).willReturn(Optional.of(member));

            PutMemberPasswordReqDto command = new PutMemberPasswordReqDto("testuser", currentPassword, currentPassword, currentPassword);

            // act & assert
            assertThatThrownBy(() -> memberService.putPassword(command))
                .isInstanceOf(CoreException.class)
                .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}

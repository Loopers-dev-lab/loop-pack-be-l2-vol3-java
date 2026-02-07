package com.loopers.domain.member;

import com.loopers.application.member.MemberInfo;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberService = new MemberService(memberRepository, passwordEncoder);
    }

    @DisplayName("회원 가입")
    @Nested
    class Register {

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // Arrange
            String loginId = "testuser";
            given(passwordEncoder.encode("Test1234!")).willReturn("encrypted");
            given(memberRepository.findByLoginId(loginId))
                .willReturn(Optional.of(
                    new Member(loginId, "encrypted", "홍길동", "19900101", "test@example.com")
                ));

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                memberService.register(loginId, "Test1234!", "김철수", "19950505", "new@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }
    }

    @DisplayName("회원 조회")
    @Nested
    class GetMember {

        @DisplayName("존재하는 회원의 loginId로 조회하면, 회원 정보를 반환한다.")
        @Test
        void returnsMember_whenLoginIdExists() {
            // Arrange
            String loginId = "testuser";
            Member member = new Member(loginId, "encrypted", "홍길동", "19900101", "test@example.com");
            given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));

            // Act
            Member result = memberService.getMember(loginId);

            // Assert
            assertAll(
                () -> assertThat(result.getLoginId()).isEqualTo("testuser"),
                () -> assertThat(result.getName()).isEqualTo("홍길동"),
                () -> assertThat(result.getBirthday()).isEqualTo("19900101"),
                () -> assertThat(result.getEmail()).isEqualTo("test@example.com")
            );
        }

        @DisplayName("존재하지 않는 loginId로 조회하면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // Arrange
            String loginId = "nouser";
            given(memberRepository.findByLoginId(loginId)).willReturn(Optional.empty());

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                memberService.getMember(loginId)
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @DisplayName("내정보 조회")
    @Nested
    class GetMyInfo {

        @DisplayName("인증에 성공하면, 이름이 마스킹된 회원 정보를 반환한다.")
        @Test
        void returnsMaskedMemberInfo_whenAuthenticated() {
            // Arrange
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            Member member = new Member(loginId, "encrypted", "홍길동", "19900101", "test@example.com");

            given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));
            given(passwordEncoder.matches(rawPassword, member.getPassword())).willReturn(true);

            // Act
            MemberInfo result = memberService.getMyInfo(loginId, rawPassword);

            // Assert
            assertThat(result.name()).isEqualTo("홍길*");
        }
    }

    @DisplayName("비밀번호 변경")
    @Nested
    class ChangePassword {

        @DisplayName("새 비밀번호가 기존 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsSameAsCurrent() {
            // Arrange
            String loginId = "testuser";
            String rawCurrentPassword = "Current1!";
            String rawNewPassword = "Current1!";
            Member member = new Member(loginId, "encryptedCurrent", "홍길동", "19900101", "test@example.com");

            given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));
            given(passwordEncoder.matches(rawCurrentPassword, member.getPassword())).willReturn(true);
            given(passwordEncoder.matches(rawNewPassword, member.getPassword())).willReturn(true);

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                memberService.changePassword(loginId, rawCurrentPassword, rawNewPassword)
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 새 비밀번호이면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenNewPasswordIsValid() {
            // Arrange
            String loginId = "testuser";
            String rawCurrentPassword = "Current1!";
            String rawNewPassword = "NewPass1!";
            String encryptedNewPassword = "encryptedNew";
            Member member = new Member(loginId, "encryptedCurrent", "홍길동", "19900101", "test@example.com");

            given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));
            given(passwordEncoder.matches(rawCurrentPassword, member.getPassword())).willReturn(true);
            given(passwordEncoder.matches(rawNewPassword, member.getPassword())).willReturn(false);
            given(passwordEncoder.encode(rawNewPassword)).willReturn(encryptedNewPassword);

            // Act
            memberService.changePassword(loginId, rawCurrentPassword, rawNewPassword);

            // Assert
            verify(passwordEncoder).encode(rawNewPassword);
        }

        @DisplayName("현재 비밀번호가 틀리면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenCurrentPasswordIsWrong() {
            // Arrange
            String loginId = "testuser";
            String rawCurrentPassword = "WrongPw1!";
            String rawNewPassword = "NewPass1!";
            Member member = new Member(loginId, "encryptedCurrent", "홍길동", "19900101", "test@example.com");

            given(memberRepository.findByLoginId(loginId)).willReturn(Optional.of(member));
            given(passwordEncoder.matches(rawCurrentPassword, member.getPassword())).willReturn(false);

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                memberService.changePassword(loginId, rawCurrentPassword, rawNewPassword)
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

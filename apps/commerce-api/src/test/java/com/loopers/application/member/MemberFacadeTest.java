package com.loopers.application.member;

import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberFacadeTest {

    @Mock
    private MemberService memberService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MemberFacade memberFacade;

    @BeforeEach
    void setUp() {
        memberFacade = new MemberFacade(memberService, passwordEncoder);
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

            given(memberService.getMember(loginId)).willReturn(member);
            given(passwordEncoder.matches(rawCurrentPassword, member.getPassword())).willReturn(true);
            given(passwordEncoder.matches(rawNewPassword, member.getPassword())).willReturn(true);

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                memberFacade.changePassword(loginId, rawCurrentPassword, rawNewPassword)
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

            given(memberService.getMember(loginId)).willReturn(member);
            given(passwordEncoder.matches(rawCurrentPassword, member.getPassword())).willReturn(true);
            given(passwordEncoder.matches(rawNewPassword, member.getPassword())).willReturn(false);
            given(passwordEncoder.encode(rawNewPassword)).willReturn(encryptedNewPassword);

            // Act
            memberFacade.changePassword(loginId, rawCurrentPassword, rawNewPassword);

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

            given(memberService.getMember(loginId)).willReturn(member);
            given(passwordEncoder.matches(rawCurrentPassword, member.getPassword())).willReturn(false);

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                memberFacade.changePassword(loginId, rawCurrentPassword, rawNewPassword)
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

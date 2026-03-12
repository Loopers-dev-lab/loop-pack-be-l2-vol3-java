package com.loopers.domain.member;

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

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberAuthServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MemberAuthService memberAuthService;

    @BeforeEach
    void setUp() {
        memberAuthService = new MemberAuthService(memberRepository, passwordEncoder);
    }

    @DisplayName("회원 인증")
    @Nested
    class Authenticate {

        @DisplayName("올바른 자격 증명이면 회원을 반환한다")
        @Test
        void returnsMemberOnValidCredentials() {
            // given
            String loginId = "kwonmo";
            String rawPassword = "Test1234!";
            MemberModel member = new MemberModel(
                new LoginId(loginId), "encoded", new MemberName("양권모"),
                LocalDate.of(1998, 9, 16), new Email("kwonmo@example.com"));

            when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(member));
            when(passwordEncoder.matches(rawPassword, "encoded")).thenReturn(true);

            // when
            MemberModel result = memberAuthService.authenticate(loginId, rawPassword);

            // then
            assertThat(result.loginId().value()).isEqualTo(loginId);
        }

        @DisplayName("존재하지 않는 loginId면 MEMBER_NOT_FOUND 예외를 던진다")
        @Test
        void throwsOnNonExistentLoginId() {
            // given
            when(memberRepository.findByLoginId("nobody")).thenReturn(Optional.empty());

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                memberAuthService.authenticate("nobody", "Test1234!"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.MEMBER_NOT_FOUND);
            verify(passwordEncoder, never()).matches(any(), any());
        }

        @DisplayName("비밀번호가 틀리면 인증 실패 예외를 던진다")
        @Test
        void throwsOnWrongPassword() {
            // given
            MemberModel member = new MemberModel(
                new LoginId("kwonmo"), "encoded", new MemberName("양권모"),
                LocalDate.of(1998, 9, 16), new Email("kwonmo@example.com"));

            when(memberRepository.findByLoginId("kwonmo")).thenReturn(Optional.of(member));
            when(passwordEncoder.matches("WrongPass1!", "encoded")).thenReturn(false);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                memberAuthService.authenticate("kwonmo", "WrongPass1!"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED);
        }
    }
}

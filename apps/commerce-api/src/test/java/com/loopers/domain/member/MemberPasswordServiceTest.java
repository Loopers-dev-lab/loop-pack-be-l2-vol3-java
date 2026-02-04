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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MemberPasswordServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MemberPasswordService memberPasswordService;

    @BeforeEach
    void setUp() {
        memberPasswordService = new MemberPasswordService(memberRepository, passwordEncoder);
    }

    @DisplayName("비밀번호 변경")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 요청이면 새 비밀번호로 암호화해서 저장한다")
        @Test
        void encodesAndSavesNewPassword() {
            // given
            MemberModel member = new MemberModel(
                new LoginId("kwonmo"), "currentEncoded", new MemberName("양권모"),
                LocalDate.of(1998, 9, 16), new Email("kwonmo@example.com"));

            when(passwordEncoder.matches("Current1234!", "currentEncoded")).thenReturn(true);
            when(passwordEncoder.matches("NewPass5678!", "currentEncoded")).thenReturn(false);
            when(passwordEncoder.encode("NewPass5678!")).thenReturn("newEncoded");
            when(passwordEncoder.matches("NewPass5678!", "newEncoded")).thenReturn(true);

            // when
            memberPasswordService.changePassword(member, "Current1234!", "NewPass5678!");

            // then
            assertThat(member.matchesPassword("NewPass5678!", passwordEncoder)).isTrue();
            verify(memberRepository).save(member);
            verify(passwordEncoder).encode("NewPass5678!");
        }

        @DisplayName("현재 비밀번호가 틀리면 저장하지 않고 예외를 던진다")
        @Test
        void throwsOnWrongCurrentPassword() {
            // given
            MemberModel member = new MemberModel(
                new LoginId("kwonmo"), "currentEncoded", new MemberName("양권모"),
                LocalDate.of(1998, 9, 16), new Email("kwonmo@example.com"));

            when(passwordEncoder.matches("WrongPass1!", "currentEncoded")).thenReturn(false);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                memberPasswordService.changePassword(member, "WrongPass1!", "NewPass5678!"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
            verify(memberRepository, never()).save(any());
            verify(passwordEncoder, never()).encode(any());
        }

        @DisplayName("새 비밀번호가 현재와 같으면 저장하지 않는다")
        @Test
        void throwsWhenNewPasswordSameAsOld() {
            // given
            MemberModel member = new MemberModel(
                new LoginId("kwonmo"), "currentEncoded", new MemberName("양권모"),
                LocalDate.of(1998, 9, 16), new Email("kwonmo@example.com"));

            when(passwordEncoder.matches("Test1234!", "currentEncoded")).thenReturn(true);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                memberPasswordService.changePassword(member, "Test1234!", "Test1234!"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PASSWORD_SAME_AS_OLD);
            verify(memberRepository, never()).save(any());
        }

        @DisplayName("새 비밀번호가 규칙에 맞지 않으면 INVALID_PASSWORD 예외를 던진다")
        @Test
        void throwsOnInvalidNewPassword() {
            // given
            MemberModel member = new MemberModel(
                new LoginId("kwonmo"), "currentEncoded", new MemberName("양권모"),
                LocalDate.of(1998, 9, 16), new Email("kwonmo@example.com"));

            when(passwordEncoder.matches("Current1234!", "currentEncoded")).thenReturn(true);

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                memberPasswordService.changePassword(member, "Current1234!", "short"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
            verify(memberRepository, never()).save(any());
        }
    }
}

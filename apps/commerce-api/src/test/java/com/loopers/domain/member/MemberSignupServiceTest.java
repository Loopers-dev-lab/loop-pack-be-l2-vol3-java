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
class MemberSignupServiceTest {

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private MemberSignupService memberSignupService;

    @BeforeEach
    void setUp() {
        memberSignupService = new MemberSignupService(memberRepository, passwordEncoder);
    }

    @DisplayName("회원가입")
    @Nested
    class Signup {

        @DisplayName("유효한 정보로 가입하면 비밀번호를 암호화하고 저장한다")
        @Test
        void encodesPasswordAndSaves() {
            // given
            String loginId = "kwonmo";
            String rawPassword = "Test1234!";
            String encodedPassword = "encoded_password";
            LocalDate birthDate = LocalDate.of(1998, 9, 16);

            when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(rawPassword)).thenReturn(encodedPassword);
            when(memberRepository.save(any(MemberModel.class))).thenAnswer(invocation -> invocation.getArgument(0));
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

            // when
            MemberModel result = memberSignupService.signup(loginId, rawPassword, "양권모", birthDate, "kwonmo@example.com");

            // then
            assertThat(result.matchesPassword(rawPassword, passwordEncoder)).isTrue();
            verify(memberRepository).save(any(MemberModel.class));
            verify(passwordEncoder).encode(rawPassword);
        }

        @DisplayName("이미 사용 중인 loginId면 저장하지 않고 예외를 던진다")
        @Test
        void throwsOnDuplicateLoginId() {
            // given
            String loginId = "kwonmo";
            MemberModel existing = new MemberModel(
                new LoginId(loginId), "encoded", new MemberName("기존회원"),
                LocalDate.of(1998, 9, 16), new Email("exist@example.com"));
            when(memberRepository.findByLoginId(loginId)).thenReturn(Optional.of(existing));

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                memberSignupService.signup(loginId, "Test1234!", "양권모",
                    LocalDate.of(1998, 9, 16), "kwonmo@example.com"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.DUPLICATE_LOGIN_ID);
            verify(memberRepository, never()).save(any());
        }

        @DisplayName("loginId 형식이 잘못되면 repository를 조회하지 않는다")
        @Test
        void skipsRepositoryOnInvalidLoginId() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                memberSignupService.signup("test@user!", "Test1234!", "양권모",
                    LocalDate.of(1998, 9, 16), "kwonmo@example.com"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID);
            verify(memberRepository, never()).findByLoginId(any());
            verify(memberRepository, never()).save(any());
        }

        @DisplayName("비밀번호 규칙 위반이면 repository를 조회하지 않는다")
        @Test
        void skipsRepositoryOnInvalidPassword() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                memberSignupService.signup("kwonmo", "short", "양권모",
                    LocalDate.of(1998, 9, 16), "kwonmo@example.com"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
            verify(memberRepository, never()).findByLoginId(any());
        }
    }
}

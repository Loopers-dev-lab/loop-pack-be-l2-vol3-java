package com.loopers.domain.user;

import com.loopers.domain.user.policy.PasswordPolicy;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String LOGIN_ID = "testuser1";
    private static final String RAW_PASSWORD = "Test1234!";
    private static final String NAME = "홍길동";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 15);
    private static final String EMAIL = "test@example.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private PasswordPolicy passwordPolicy;

    @InjectMocks
    private UserService userService;

    @DisplayName("회원가입 시,")
    @Nested
    class Signup {

        @DisplayName("loginId가 중복이면 CONFLICT 예외를 던진다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // arrange
            when(userRepository.existsByLoginId(LOGIN_ID)).thenReturn(true);

            // act
            CoreException ex = assertThrows(CoreException.class, () ->
                    userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL)
            );

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("유효한 정보로 회원가입하면 저장된 사용자를 반환한다.")
        @Test
        void returnsUser_whenValid() {
            // arrange
            when(userRepository.existsByLoginId(LOGIN_ID)).thenReturn(false);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("encoded-password");
            when(userRepository.save(any(UserModel.class))).thenAnswer(inv -> inv.getArgument(0));

            // act
            UserModel saved = userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // assert
            assertThat(saved).isNotNull();
            assertThat(saved.getLoginId()).isEqualTo(LOGIN_ID);
            assertThat(saved.getPassword()).isEqualTo("encoded-password");
            assertThat(saved.getName()).isEqualTo(NAME);
            assertThat(saved.getBirthDate()).isEqualTo(BIRTH_DATE);
            assertThat(saved.getEmail()).isEqualTo(EMAIL);
        }

        @DisplayName("비밀번호 정책 클래스를 한 번 호출한다.")
        @Test
        void callsPasswordPolicy_once() {
            // arrange
            when(userRepository.existsByLoginId(LOGIN_ID)).thenReturn(false);
            when(passwordEncoder.encode(RAW_PASSWORD)).thenReturn("encoded-password");
            when(userRepository.save(any(UserModel.class))).thenAnswer(inv -> inv.getArgument(0));

            // act
            userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // assert
            verify(passwordPolicy, times(1)).validateForSignup(RAW_PASSWORD, BIRTH_DATE);
        }

        @DisplayName("비밀번호 정책 위반 시 BAD_REQUEST 예외를 던진다.")
        @Test
        void throwsBadRequest_whenPolicyFails() {
            // arrange
            when(userRepository.existsByLoginId(LOGIN_ID)).thenReturn(false);
            doThrow(new CoreException(ErrorType.BAD_REQUEST, "policy fail"))
                    .when(passwordPolicy).validateForSignup(RAW_PASSWORD, BIRTH_DATE);

            // act
            CoreException ex = assertThrows(CoreException.class, () ->
                    userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL)
            );

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("인증 시,")
    @Nested
    class Authenticate {

        @DisplayName("loginId가 blank면 UNAUTHORIZED 예외를 던진다.")
        @Test
        void throwsUnauthorized_whenLoginIdIsBlank() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                    () -> userService.authenticate(" ", RAW_PASSWORD));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("password가 blank면 UNAUTHORIZED 예외를 던진다.")
        @Test
        void throwsUnauthorized_whenPasswordIsBlank() {
            // act
            CoreException ex = assertThrows(CoreException.class,
                    () -> userService.authenticate(LOGIN_ID, "   "));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("유저가 없으면 UNAUTHORIZED 예외를 던진다.")
        @Test
        void throwsUnauthorized_whenUserNotFound() {
            // arrange
            when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.empty());

            // act
            CoreException ex = assertThrows(CoreException.class,
                    () -> userService.authenticate(LOGIN_ID, RAW_PASSWORD));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 틀리면 UNAUTHORIZED 예외를 던진다.")
        @Test
        void throwsUnauthorized_whenPasswordMismatch() {
            // arrange
            UserModel user = mock(UserModel.class);
            when(user.getPassword()).thenReturn("encoded");
            when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, "encoded")).thenReturn(false);

            // act
            CoreException ex = assertThrows(CoreException.class,
                    () -> userService.authenticate(LOGIN_ID, RAW_PASSWORD));

            // assert
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("자격 증명이 올바르면 user를 반환한다.")
        @Test
        void returnsUser_whenValid() {
            // arrange
            UserModel user = mock(UserModel.class);
            when(userRepository.findByLoginId(LOGIN_ID)).thenReturn(Optional.of(user));
            when(user.getPassword()).thenReturn("encoded");
            when(passwordEncoder.matches(RAW_PASSWORD, "encoded")).thenReturn(true);

            // act
            UserModel authenticated = userService.authenticate(LOGIN_ID, RAW_PASSWORD);

            // assert
            assertThat(authenticated).isSameAs(user);
        }
    }
}
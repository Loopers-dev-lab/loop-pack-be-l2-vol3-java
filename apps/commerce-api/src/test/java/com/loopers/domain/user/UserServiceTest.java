package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final String VALID_LOGIN_ID = "namjin123";
    private static final String VALID_PASSWORD = "qwer@1234";
    private static final String VALID_ENCRYPTED_PASSWORD = "encrypted_password";
    private static final String VALID_NAME = "namjin";
    private static final String VALID_BIRTHDAY = "1994-05-25";
    private static final String VALID_EMAIL = "epemxksl@gmail.com";

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @DisplayName("회원가입을 할 때, ")
    @Nested
    class Signup {

        @DisplayName("정상적인 정보로 회원가입이 성공한다.")
        @Test
        void signupSucceeds_whenInfoIsValid() {
            // arrange
            // stub: existsByLoginId 호출하면 false 반환 (해당 아이디로 가입된 회원 없음)
            when(userRepository.existsByLoginId(VALID_LOGIN_ID))
                    .thenReturn(false);

            // stub: 비밀번호 암호화
            when(passwordEncoder.encode(VALID_PASSWORD))
                    .thenReturn("encrypted_password");

            // stub: save 호출 시 저장된 객체 반환
            when(userRepository.save(any(UserModel.class)))
                    .thenAnswer((invocation) -> invocation.getArgument(0));

            // act
            UserModel result = userService.signup(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID);

            verify(userRepository, times(1)).save(any(UserModel.class));
        }

        @DisplayName("이미 가입된 로그인 ID로 가입하면, 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdAlreadyExists() {
            // arrange
            when(userRepository.existsByLoginId(VALID_LOGIN_ID))
                    .thenReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.signup(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);

            // 행위 검증
            verify(userRepository, never()).save(any());
        }
    }

    @DisplayName("인증을 할 때, ")
    @Nested
    class Authenticate {

        @DisplayName("올바른 로그인 ID와 비밀번호로 인증이 성공한다.")
        @Test
        void authenticateSucceeds_whenCredentialsAreValid() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCRYPTED_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            when(userRepository.findByLoginId(VALID_LOGIN_ID))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(VALID_PASSWORD, VALID_ENCRYPTED_PASSWORD))
                    .thenReturn(true);

            // act
            UserModel result = userService.authenticate(VALID_LOGIN_ID, VALID_PASSWORD);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID);
        }

        @DisplayName("존재하지 않는 로그인 ID로 인증하면, 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdNotFound() {
            // arrange
            when(userRepository.findByLoginId(VALID_LOGIN_ID))
                    .thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.authenticate(VALID_LOGIN_ID, VALID_PASSWORD);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("비밀번호가 일치하지 않으면, 예외가 발생한다.")
        @Test
        void throwsException_whenPasswordDoesNotMatch() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCRYPTED_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            when(userRepository.findByLoginId(VALID_LOGIN_ID))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPassword", VALID_ENCRYPTED_PASSWORD))
                    .thenReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.authenticate(VALID_LOGIN_ID, "wrongPassword");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        private static final String NEW_PASSWORD = "newPw@1234";
        private static final String NEW_ENCRYPTED_PASSWORD = "new_encrypted_password";

        @DisplayName("정상적인 정보로 비밀번호 변경이 성공한다.")
        @Test
        void changePasswordSucceeds_whenInfoIsValid() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCRYPTED_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            when(userRepository.findByLoginId(VALID_LOGIN_ID))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(VALID_PASSWORD, VALID_ENCRYPTED_PASSWORD))
                    .thenReturn(true);
            when(passwordEncoder.matches(NEW_PASSWORD, VALID_ENCRYPTED_PASSWORD))
                    .thenReturn(false);
            when(passwordEncoder.encode(NEW_PASSWORD))
                    .thenReturn(NEW_ENCRYPTED_PASSWORD);
            when(userRepository.save(any(UserModel.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // act
            userService.changePassword(VALID_LOGIN_ID, VALID_PASSWORD, NEW_PASSWORD);

            // assert
            verify(userRepository, times(1)).save(any(UserModel.class));
        }

        @DisplayName("존재하지 않는 사용자이면, 예외가 발생한다.")
        @Test
        void throwsException_whenUserNotFound() {
            // arrange
            when(userRepository.findByLoginId(VALID_LOGIN_ID))
                    .thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(VALID_LOGIN_ID, VALID_PASSWORD, NEW_PASSWORD);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
            verify(userRepository, never()).save(any());
        }

        @DisplayName("현재 비밀번호가 일치하지 않으면, 예외가 발생한다.")
        @Test
        void throwsException_whenCurrentPasswordDoesNotMatch() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCRYPTED_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            when(userRepository.findByLoginId(VALID_LOGIN_ID))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPw@123", VALID_ENCRYPTED_PASSWORD))
                    .thenReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(VALID_LOGIN_ID, "wrongPw@123", NEW_PASSWORD);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(userRepository, never()).save(any());
        }

        @DisplayName("새 비밀번호가 현재 비밀번호와 동일하면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewPasswordIsSameAsCurrent() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCRYPTED_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            when(userRepository.findByLoginId(VALID_LOGIN_ID))
                    .thenReturn(Optional.of(user));
            // currentPassword == newPassword == VALID_PASSWORD 이므로,
            // 1차 호출(현재 비밀번호 확인)과 2차 호출(새 비밀번호 == 현재 비밀번호 확인) 모두 이 stub에 매칭된다
            when(passwordEncoder.matches(VALID_PASSWORD, VALID_ENCRYPTED_PASSWORD))
                    .thenReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(VALID_LOGIN_ID, VALID_PASSWORD, VALID_PASSWORD);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(userRepository, never()).save(any());
        }

        @DisplayName("새 비밀번호가 규칙에 위반되면, 예외가 발생한다.")
        @Test
        void throwsException_whenNewPasswordViolatesRules() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCRYPTED_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            when(userRepository.findByLoginId(VALID_LOGIN_ID))
                    .thenReturn(Optional.of(user));
            when(passwordEncoder.matches(VALID_PASSWORD, VALID_ENCRYPTED_PASSWORD))
                    .thenReturn(true);
            when(passwordEncoder.matches("short", VALID_ENCRYPTED_PASSWORD))
                    .thenReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(VALID_LOGIN_ID, VALID_PASSWORD, "short");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
            verify(userRepository, never()).save(any());
        }
    }

    @DisplayName("로그인 ID로 조회할 때, ")
    @Nested
    class FindByLoginId {

        @DisplayName("존재하는 로그인 ID로 조회하면, 사용자를 반환한다.")
        @Test
        void returnsUser_whenLoginIdExists() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_ENCRYPTED_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            when(userRepository.findByLoginId(VALID_LOGIN_ID))
                    .thenReturn(Optional.of(user));

            // act
            UserModel result = userService.findByLoginId(VALID_LOGIN_ID);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(VALID_LOGIN_ID);
        }

        @DisplayName("존재하지 않는 로그인 ID로 조회하면, 예외가 발생한다.")
        @Test
        void throwsException_whenLoginIdNotFound() {
            // arrange
            when(userRepository.findByLoginId(VALID_LOGIN_ID))
                    .thenReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.findByLoginId(VALID_LOGIN_ID);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }
}

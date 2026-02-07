package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.loopers.application.user.UserInfo;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        userService = new UserService(userRepository, passwordEncoder);
    }

    @DisplayName("회원가입")
    @Nested
    class SignUp {

        @DisplayName("이미 존재하는 로그인 ID로 가입하면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflict_whenLoginIdAlreadyExists() {
            // Arrange
            String loginId = "testuser";
            when(userRepository.findByLoginId(loginId))
                .thenReturn(Optional.of(new User(loginId, "encrypted", "홍길동", "19900101", "test@example.com")));

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.signUp(loginId, "Test1234!", "김철수", "19950505", "new@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // Arrange
            String loginId = "newuser";
            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.empty());

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.signUp(loginId, "Short1!", "홍길동", "19900101", "test@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // Arrange
            String loginId = "newuser";
            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.empty());

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.signUp(loginId, "VeryLongPassword1!", "홍길동", "19900101", "test@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 허용되지 않는 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsInvalidChars() {
            // Arrange
            String loginId = "newuser";
            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.empty());

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.signUp(loginId, "Test1234한글", "홍길동", "19900101", "test@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthday() {
            // Arrange
            String loginId = "newuser";
            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.empty());

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.signUp(loginId, "Pass19900101!", "홍길동", "19900101", "test@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 정보로 가입하면, 회원이 저장된다.")
        @Test
        void savesUser_whenInputIsValid() {
            // Arrange
            String loginId = "newuser";
            String rawPassword = "Test1234!";
            User savedUser = new User(loginId, "encrypted", "홍길동", "19900101", "test@example.com");

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(rawPassword)).thenReturn("encrypted");
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // Act
            User result = userService.signUp(loginId, rawPassword, "홍길동", "19900101", "test@example.com");

            // Assert
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo("newuser");
        }
    }

    @DisplayName("내정보 조회")
    @Nested
    class GetMyInfo {

        @DisplayName("인증에 성공하면, 마스킹된 회원 정보를 반환한다.")
        @Test
        void returnsMaskedUserInfo_whenAuthenticated() {
            // Arrange
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            User user = new User(loginId, "encrypted", "홍길동", "19900101", "test@example.com");

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(rawPassword, "encrypted")).thenReturn(true);

            // Act
            UserInfo result = userService.getMyInfo(loginId, rawPassword);

            // Assert
            assertThat(result.loginId()).isEqualTo("testuser");
            assertThat(result.name()).isEqualTo("홍길*");
        }

        @DisplayName("존재하지 않는 loginId이면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFound_whenLoginIdDoesNotExist() {
            // Arrange
            when(userRepository.findByLoginId("nouser")).thenReturn(Optional.empty());

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.getMyInfo("nouser", "Test1234!")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("비밀번호가 틀리면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsWrong() {
            // Arrange
            String loginId = "testuser";
            User user = new User(loginId, "encrypted", "홍길동", "19900101", "test@example.com");

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongpw1!", "encrypted")).thenReturn(false);

            // Act
            CoreException exception = assertThrows(CoreException.class, () ->
                userService.getMyInfo(loginId, "wrongpw1!")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

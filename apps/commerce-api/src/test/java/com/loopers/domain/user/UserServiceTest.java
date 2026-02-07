package com.loopers.domain.user;

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
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserService userService;

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepository, passwordEncoder);
    }

    @DisplayName("회원가입을 할 때,")
    @Nested
    class Register {

        @DisplayName("정상적인 정보가 주어지면, 회원이 저장된다.")
        @Test
        void savesUser_whenValidInfoIsProvided() {
            // arrange
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            String encodedPassword = "encodedPassword123";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String email = "test@example.com";

            given(userRepository.findByLoginId(loginId)).willReturn(Optional.empty());
            given(passwordEncoder.encode(rawPassword)).willReturn(encodedPassword);
            given(userRepository.save(any(UserModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            UserModel result = userService.register(loginId, rawPassword, name, birthDate, email);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(loginId);
            verify(userRepository).save(any(UserModel.class));
        }

        @DisplayName("이미 존재하는 로그인 ID가 주어지면, CONFLICT 예외가 발생한다.")
        @Test
        void throwsConflictException_whenLoginIdAlreadyExists() {
            // arrange
            String loginId = "existinguser";
            String rawPassword = "Test1234!";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String email = "test@example.com";

            UserModel existingUser = new UserModel(loginId, rawPassword, name, birthDate, email);
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(existingUser));

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.register(loginId, rawPassword, name, birthDate, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @DisplayName("비밀번호는 암호화되어 저장된다.")
        @Test
        void savesUserWithEncodedPassword() {
            // arrange
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            String encodedPassword = "encodedPassword123";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String email = "test@example.com";

            given(userRepository.findByLoginId(loginId)).willReturn(Optional.empty());
            given(passwordEncoder.encode(rawPassword)).willReturn(encodedPassword);
            given(userRepository.save(any(UserModel.class))).willAnswer(invocation -> invocation.getArgument(0));

            // act
            UserModel result = userService.register(loginId, rawPassword, name, birthDate, email);

            // assert
            verify(passwordEncoder).encode(rawPassword);
            assertThat(result.getPassword().getValue()).isEqualTo(encodedPassword);
        }
    }

    @DisplayName("내 정보를 조회할 때,")
    @Nested
    class GetMyInfo {

        @DisplayName("정상적인 로그인 ID와 비밀번호가 주어지면, 유저 정보를 반환한다.")
        @Test
        void returnsUserInfo_whenValidCredentialsAreProvided() {
            // arrange
            String loginId = "testuser";
            String rawPassword = "Test1234!";
            String encodedPassword = "encodedPassword123";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String email = "test@example.com";

            UserModel user = UserModel.createWithEncodedPassword(loginId, encodedPassword, name, birthDate, email);
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(true);

            // act
            UserModel result = userService.getMyInfo(loginId, rawPassword);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(loginId);
        }

        @DisplayName("존재하지 않는 로그인 ID가 주어지면, NOT_FOUND 예외가 발생한다.")
        @Test
        void throwsNotFoundException_whenLoginIdNotFound() {
            // arrange
            String loginId = "nonexistent";
            String rawPassword = "Test1234!";

            given(userRepository.findByLoginId(loginId)).willReturn(Optional.empty());

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.getMyInfo(loginId, rawPassword);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @DisplayName("잘못된 비밀번호가 주어지면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorizedException_whenPasswordIsInvalid() {
            // arrange
            String loginId = "testuser";
            String rawPassword = "WrongPass1!";
            String encodedPassword = "encodedPassword123";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String email = "test@example.com";

            UserModel user = UserModel.createWithEncodedPassword(loginId, encodedPassword, name, birthDate, email);
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(rawPassword, encodedPassword)).willReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.getMyInfo(loginId, rawPassword);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("비밀번호를 수정할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("정상적인 기존 비밀번호와 새 비밀번호가 주어지면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenValidPasswordsAreProvided() {
            // arrange
            String loginId = "testuser";
            String currentPassword = "Test1234!";
            String currentEncodedPassword = "encodedCurrentPassword";
            String newPassword = "NewPass12!";
            String newEncodedPassword = "encodedNewPassword";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String email = "test@example.com";

            UserModel user = UserModel.createWithEncodedPassword(loginId, currentEncodedPassword, name, birthDate, email);
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(currentPassword, currentEncodedPassword)).willReturn(true);
            given(passwordEncoder.matches(newPassword, currentEncodedPassword)).willReturn(false);
            given(passwordEncoder.encode(newPassword)).willReturn(newEncodedPassword);

            // act
            userService.changePassword(loginId, currentPassword, newPassword);

            // assert
            assertThat(user.getPassword().getValue()).isEqualTo(newEncodedPassword);
        }

        @DisplayName("기존 비밀번호가 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorizedException_whenCurrentPasswordIsInvalid() {
            // arrange
            String loginId = "testuser";
            String wrongCurrentPassword = "WrongPass1!";
            String currentEncodedPassword = "encodedCurrentPassword";
            String newPassword = "NewPass12!";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String email = "test@example.com";

            UserModel user = UserModel.createWithEncodedPassword(loginId, currentEncodedPassword, name, birthDate, email);
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(wrongCurrentPassword, currentEncodedPassword)).willReturn(false);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(loginId, wrongCurrentPassword, newPassword);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @DisplayName("새 비밀번호가 기존 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewPasswordIsSameAsCurrent() {
            // arrange
            String loginId = "testuser";
            String currentPassword = "Test1234!";
            String currentEncodedPassword = "encodedCurrentPassword";
            String newPassword = "Test1234!";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String email = "test@example.com";

            UserModel user = UserModel.createWithEncodedPassword(loginId, currentEncodedPassword, name, birthDate, email);
            given(userRepository.findByLoginId(loginId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(currentPassword, currentEncodedPassword)).willReturn(true);
            given(passwordEncoder.matches(newPassword, currentEncodedPassword)).willReturn(true);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.changePassword(loginId, currentPassword, newPassword);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

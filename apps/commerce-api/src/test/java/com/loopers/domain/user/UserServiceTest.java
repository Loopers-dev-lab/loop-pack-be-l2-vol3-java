package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorMessage;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @DisplayName("회원 가입")
    @Nested
    class CreateUser {

        @Test
        @DisplayName("비밀번호에 생년월일이 포함되면 예외가 발생한다")
        void fail_when_password_contains_birthDate() {
            // arrange
            String loginId = "user123";
            String rawPassword = "Pass19911203!";
            LocalDate birthDate = LocalDate.of(1991, 12, 3);
            String name = "김용권";
            String email = "yk@google.com";

            // act & assert
            assertThatThrownBy(() -> userService.createUser(loginId, rawPassword, birthDate, name, email))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining(ErrorMessage.User.PASSWORD_CONTAINS_BIRTH_DATE);
        }

        @Test
        @DisplayName("이미 존재하는 이메일이면 예외가 발생한다")
        void fail_when_email_already_exists() {
            String loginId = "user123";
            String rawPassword = "Password1!";
            LocalDate birthDate = LocalDate.of(1991, 12, 3);
            String name = "김용권";
            String email = "yk@google.com";

            given(userRepository.existsByEmail(email)).willReturn(true);

            assertThatThrownBy(() -> userService.createUser(loginId, rawPassword, birthDate, name, email))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining(ErrorMessage.User.EMAIL_ALREADY_EXISTS);
        }

        @Test
        @DisplayName("이미 존재하는 로그인 아이디면 예외가 발생한다")
        void fail_when_loginId_already_exists() {
            String loginId = "user123";
            String rawPassword = "Password1!";
            LocalDate birthDate = LocalDate.of(1991, 12, 3);
            String name = "김용권";
            String email = "yk@google.com";

            given(userRepository.existsByLoginId(loginId)).willReturn(true);

            assertThatThrownBy(() -> userService.createUser(loginId, rawPassword, birthDate, name, email))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining(ErrorMessage.User.LOGIN_ID_ALREADY_EXISTS);
        }
    }

    @DisplayName("비밀번호 변경")
    @Nested
    class ChangePassword {

        @Test
        @DisplayName("비밀번호 변경에 성공한다")
        void success() {
            Long userId = 1L;
            String currentPassword = "OldPass1!";
            String newPassword = "NewPass1!";
            String encodedCurrentPassword = "encoded_old";
            String encodedNewPassword = "encoded_new";

            UserModel user = UserModel.create("user123", encodedCurrentPassword, LocalDate.of(1991, 12, 3), "김용권", "yk@google.com");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(currentPassword, encodedCurrentPassword)).willReturn(true);
            given(passwordEncoder.matches(newPassword, encodedCurrentPassword)).willReturn(false);
            given(passwordEncoder.encode(newPassword)).willReturn(encodedNewPassword);

            userService.changePassword(userId, currentPassword, newPassword);

            assertThat(user.getPassword()).isEqualTo(encodedNewPassword);
        }

        @Test
        @DisplayName("기존 비밀번호가 일치하지 않으면 예외가 발생한다")
        void fail_when_currentPassword_not_match() {
            Long userId = 1L;
            String currentPassword = "WrongPass!";
            String newPassword = "NewPass1!";
            String encodedCurrentPassword = "encoded_old";

            UserModel user = UserModel.create("user123", encodedCurrentPassword, LocalDate.of(1991, 12, 3), "김용권", "yk@google.com");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(currentPassword, encodedCurrentPassword)).willReturn(false);

            assertThatThrownBy(() -> userService.changePassword(userId, currentPassword, newPassword))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining(ErrorMessage.User.CURRENT_PASSWORD_MISMATCH);
        }

        @Test
        @DisplayName("새 비밀번호가 기존 비밀번호와 같으면 예외가 발생한다")
        void fail_when_newPassword_same_as_current() {
            Long userId = 1L;
            String currentPassword = "SamePass1!";
            String newPassword = "SamePass1!";
            String encodedCurrentPassword = "encoded_same";

            UserModel user = UserModel.create("user123", encodedCurrentPassword, LocalDate.of(1991, 12, 3), "김용권", "yk@google.com");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(currentPassword, encodedCurrentPassword)).willReturn(true);
            given(passwordEncoder.matches(newPassword, encodedCurrentPassword)).willReturn(true);

            assertThatThrownBy(() -> userService.changePassword(userId, currentPassword, newPassword))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining(ErrorMessage.User.NEW_PASSWORD_SAME_AS_CURRENT);
        }

        @Test
        @DisplayName("새 비밀번호에 생년월일이 포함되면 예외가 발생한다")
        void fail_when_newPassword_contains_birthDate() {
            // arrange
            Long userId = 1L;
            String currentPassword = "OldPass1!";
            String newPassword = "Pass19911203!";
            String encodedCurrentPassword = "encoded_old";

            UserModel user = UserModel.create("user123", encodedCurrentPassword, LocalDate.of(1991, 12, 3), "김용권", "yk@google.com");

            given(userRepository.findById(userId)).willReturn(Optional.of(user));
            given(passwordEncoder.matches(currentPassword, encodedCurrentPassword)).willReturn(true);
            given(passwordEncoder.matches(newPassword, encodedCurrentPassword)).willReturn(false);

            // act & assert
            assertThatThrownBy(() -> userService.changePassword(userId, currentPassword, newPassword))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining(ErrorMessage.User.PASSWORD_CONTAINS_BIRTH_DATE);
        }

        @Test
        @DisplayName("사용자를 찾을 수 없으면 예외가 발생한다")
        void fail_when_user_not_found() {
            // arrange
            Long userId = 999L;
            String currentPassword = "OldPass1!";
            String newPassword = "NewPass1!";

            given(userRepository.findById(userId)).willReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> userService.changePassword(userId, currentPassword, newPassword))
                .isInstanceOf(CoreException.class)
                .hasMessageContaining(ErrorMessage.User.USER_NOT_FOUND);
        }
    }
}

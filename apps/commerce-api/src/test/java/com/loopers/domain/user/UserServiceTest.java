package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 단위 테스트")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    @Nested
    @DisplayName("회원가입")
    class Register {

        @Test
        @DisplayName("유효한 정보로 회원가입하면 성공한다")
        void success() {
            // given
            String loginId = "testuser";
            String password = "Password1!";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String email = "test@example.com";

            when(userRepository.existsByLoginId(loginId)).thenReturn(false);
            when(passwordEncoder.encode(password)).thenReturn("$2a$10$encryptedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            User result = userService.register(loginId, password, name, birthDate, email);

            // then
            assertThat(result).isNotNull();
            verify(userRepository).existsByLoginId(loginId);
            verify(passwordEncoder).encode(password);
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("이미 존재하는 로그인 ID로 회원가입하면 CONFLICT 예외가 발생한다")
        void throwsConflict_whenLoginIdExists() {
            // given
            String loginId = "existinguser";
            when(userRepository.existsByLoginId(loginId)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.register(
                loginId,
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.CONFLICT);

            verify(userRepository).existsByLoginId(loginId);
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("로그인 ID가 null이면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenLoginIdIsNull() {
            // when & then
            assertThatThrownBy(() -> userService.register(
                null,
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);

            verify(userRepository, never()).existsByLoginId(anyString());
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("로그인 ID가 빈 문자열이면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenLoginIdIsEmpty() {
            // when & then
            assertThatThrownBy(() -> userService.register(
                "",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("이름이 null이면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenNameIsNull() {
            // when & then
            assertThatThrownBy(() -> userService.register(
                "testuser",
                "Password1!",
                null,
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("생년월일이 null이면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenBirthDateIsNull() {
            // when & then
            assertThatThrownBy(() -> userService.register(
                "testuser",
                "Password1!",
                "홍길동",
                null,
                "test@example.com"
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                .hasMessageContaining("생년월일");
        }

        @Test
        @DisplayName("비밀번호가 8자 미만이면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenPasswordTooShort() {
            // when & then
            assertThatThrownBy(() -> userService.register(
                "testuser",
                "Pass1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                .hasMessageContaining("8~16자");
        }

        @Test
        @DisplayName("비밀번호가 16자 초과이면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenPasswordTooLong() {
            // when & then
            assertThatThrownBy(() -> userService.register(
                "testuser",
                "Password1!Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                .hasMessageContaining("8~16자");
        }

        @Test
        @DisplayName("비밀번호에 공백이 포함되면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenPasswordContainsSpace() {
            // when & then
            assertThatThrownBy(() -> userService.register(
                "testuser",
                "Pass word1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                .hasMessageContaining("영문 대소문자, 숫자, 특수문자");
        }

        @Test
        @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenPasswordContainsBirthDateYYYYMMDD() {
            // when & then
            assertThatThrownBy(() -> userService.register(
                "testuser",
                "19900115Pw!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                .hasMessageContaining("생년월일");
        }

        @Test
        @DisplayName("비밀번호에 생년월일(yyMMdd)이 포함되면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenPasswordContainsBirthDateYYMMDD() {
            // when & then
            assertThatThrownBy(() -> userService.register(
                "testuser",
                "900115Pass!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "test@example.com"
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                .hasMessageContaining("생년월일");
        }

        @Test
        @DisplayName("이메일 형식이 올바르지 않으면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenEmailFormatInvalid() {
            // when & then
            assertThatThrownBy(() -> userService.register(
                "testuser",
                "Password1!",
                "홍길동",
                LocalDate.of(1990, 1, 15),
                "invalid-email"
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                .hasMessageContaining("이메일");
        }
    }

    @Nested
    @DisplayName("내 정보 조회")
    class GetUserInfo {

        @Test
        @DisplayName("유효한 로그인 ID와 비밀번호로 사용자를 조회하면 성공한다")
        void success() {
            // given
            String loginId = "testuser";
            String rawPassword = "Password1!";
            String encodedPassword = "$2a$10$encryptedPassword";

            User user = User.create(loginId, encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

            // when
            User result = userService.getUserInfo(loginId, rawPassword);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(loginId);
            verify(userRepository).findByLoginId(loginId);
            verify(passwordEncoder).matches(rawPassword, encodedPassword);
        }

        @Test
        @DisplayName("존재하지 않는 로그인 ID로 조회하면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenUserNotExists() {
            // given
            String loginId = "nonexistent";
            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.getUserInfo(loginId, "Password1!"))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

            verify(userRepository).findByLoginId(loginId);
            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("비밀번호가 일치하지 않으면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenPasswordNotMatch() {
            // given
            String loginId = "testuser";
            String rawPassword = "WrongPassword1!";
            String encodedPassword = "$2a$10$encryptedPassword";

            User user = User.create(loginId, encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.getUserInfo(loginId, rawPassword))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                .hasMessageContaining("비밀번호가 일치하지 않습니다");

            verify(userRepository).findByLoginId(loginId);
            verify(passwordEncoder).matches(rawPassword, encodedPassword);
        }
    }

    @Nested
    @DisplayName("비밀번호 수정")
    class UpdatePassword {

        @Test
        @DisplayName("유효한 정보로 비밀번호를 수정하면 성공한다")
        void success() {
            // given
            String loginId = "testuser";
            String currentPassword = "Password1!";
            String newPassword = "NewPassword2!";
            String encodedCurrentPassword = "$2a$10$encryptedPassword";
            String encodedNewPassword = "$2a$10$newEncryptedPassword";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);

            User user = User.create(loginId, encodedCurrentPassword, "홍길동", birthDate, "test@example.com");

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(currentPassword, encodedCurrentPassword)).thenReturn(true);
            when(passwordEncoder.matches(newPassword, encodedCurrentPassword)).thenReturn(false);
            when(passwordEncoder.encode(newPassword)).thenReturn(encodedNewPassword);

            // when
            userService.updatePassword(loginId, currentPassword, newPassword, birthDate);

            // then
            verify(userRepository).findByLoginId(loginId);
            verify(passwordEncoder).matches(currentPassword, encodedCurrentPassword);
            verify(passwordEncoder).matches(newPassword, encodedCurrentPassword);
            verify(passwordEncoder).encode(newPassword);
        }

        @Test
        @DisplayName("존재하지 않는 사용자의 비밀번호를 수정하면 NOT_FOUND 예외가 발생한다")
        void throwsNotFound_whenUserNotExists() {
            // given
            String loginId = "nonexistent";
            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> userService.updatePassword(
                loginId,
                "Password1!",
                "NewPassword2!",
                LocalDate.of(1990, 1, 15)
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.NOT_FOUND)
                .hasMessageContaining("사용자를 찾을 수 없습니다");

            verify(userRepository).findByLoginId(loginId);
        }

        @Test
        @DisplayName("기존 비밀번호가 일치하지 않으면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenCurrentPasswordNotMatch() {
            // given
            String loginId = "testuser";
            String currentPassword = "WrongPassword1!";
            String encodedPassword = "$2a$10$encryptedPassword";

            User user = User.create(loginId, encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(currentPassword, encodedPassword)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.updatePassword(
                loginId,
                currentPassword,
                "NewPassword2!",
                LocalDate.of(1990, 1, 15)
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                .hasMessageContaining("비밀번호가 일치하지 않습니다");

            verify(userRepository).findByLoginId(loginId);
            verify(passwordEncoder).matches(currentPassword, encodedPassword);
        }

        @Test
        @DisplayName("새 비밀번호가 기존 비밀번호와 동일하면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenNewPasswordSameAsCurrent() {
            // given
            String loginId = "testuser";
            String currentPassword = "Password1!";
            String newPassword = "Password1!";
            String encodedPassword = "$2a$10$encryptedPassword";

            User user = User.create(loginId, encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(currentPassword, encodedPassword)).thenReturn(true);
            when(passwordEncoder.matches(newPassword, encodedPassword)).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.updatePassword(
                loginId,
                currentPassword,
                newPassword,
                LocalDate.of(1990, 1, 15)
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                .hasMessageContaining("현재 비밀번호와 동일한 비밀번호는 사용할 수 없습니다");

            verify(userRepository).findByLoginId(loginId);
        }

        @Test
        @DisplayName("새 비밀번호가 8자 미만이면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenNewPasswordTooShort() {
            // given
            String loginId = "testuser";
            String currentPassword = "Password1!";
            String newPassword = "Pass1!";
            String encodedPassword = "$2a$10$encryptedPassword";

            User user = User.create(loginId, encodedPassword, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(currentPassword, encodedPassword)).thenReturn(true);
            when(passwordEncoder.matches(newPassword, encodedPassword)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.updatePassword(
                loginId,
                currentPassword,
                newPassword,
                LocalDate.of(1990, 1, 15)
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                .hasMessageContaining("8~16자");
        }

        @Test
        @DisplayName("새 비밀번호에 생년월일이 포함되면 BAD_REQUEST 예외가 발생한다")
        void throwsBadRequest_whenNewPasswordContainsBirthDate() {
            // given
            String loginId = "testuser";
            String currentPassword = "Password1!";
            String newPassword = "19900115Pw!";
            String encodedPassword = "$2a$10$encryptedPassword";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);

            User user = User.create(loginId, encodedPassword, "홍길동", birthDate, "test@example.com");

            when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(currentPassword, encodedPassword)).thenReturn(true);
            when(passwordEncoder.matches(newPassword, encodedPassword)).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> userService.updatePassword(
                loginId,
                currentPassword,
                newPassword,
                birthDate
            ))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST)
                .hasMessageContaining("생년월일");
        }
    }
}

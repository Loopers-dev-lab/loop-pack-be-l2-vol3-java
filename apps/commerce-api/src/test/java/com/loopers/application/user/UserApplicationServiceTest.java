package com.loopers.application.user;

import com.loopers.application.user.command.ChangePasswordCommand;
import com.loopers.application.user.command.RegisterCommand;
import com.loopers.domain.user.PasswordEncoder;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserRepository;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
import com.loopers.domain.user.vo.Phone;
import com.loopers.domain.user.vo.UserId;
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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserApplicationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserApplicationService userApplicationService;

    private UserId userId;
    private Name name;
    private Email email;
    private BirthDate birthDate;
    private Phone phone;
    private User user;

    @BeforeEach
    void setUp() {
        userId = new UserId("testuser");
        name = new Name("홍길동");
        email = new Email("test@example.com");
        birthDate = new BirthDate(LocalDate.of(1999, 1, 15));
        phone = new Phone("010-1234-5678");
        user = new User(userId, Password.ofEncoded("$2a$10$encodedPassword"), name, email, birthDate, phone);
    }

    @Nested
    @DisplayName("회원가입")
    class RegisterTest {

        @Test
        @DisplayName("성공")
        void registerSuccess() {
            RegisterCommand command = RegisterCommand.builder()
                    .userId("testuser")
                    .rawPassword("1Q2w3e4r!")
                    .name("홍길동")
                    .email("test@example.com")
                    .birthDate("19990115")
                    .phone("010-1234-5678")
                    .build();
            User encodedUser = new User(userId, Password.ofEncoded("$2a$10$encodedPassword"), name, email, birthDate, phone);

            when(userRepository.existsByUserId(any(UserId.class))).thenReturn(false);
            when(passwordEncoder.encode("1Q2w3e4r!")).thenReturn("$2a$10$encodedPassword");
            when(userRepository.save(encodedUser)).thenReturn(encodedUser);

            User result = userApplicationService.register(command);

            assertThat(result.id().value()).isEqualTo("testuser");
            assertThat(result.password().value()).isEqualTo("$2a$10$encodedPassword");
            assertThat(result.phone().value()).isEqualTo("010-1234-5678");
        }

        @Test
        @DisplayName("실패 - 로그인 ID 중복")
        void registerFailDuplicateUserId() {
            RegisterCommand command = RegisterCommand.builder()
                    .userId("testuser")
                    .rawPassword("1Q2w3e4r!")
                    .name("홍길동")
                    .email("test@example.com")
                    .birthDate("19990115")
                    .phone("010-1234-5678")
                    .build();
            when(userRepository.existsByUserId(any(UserId.class))).thenReturn(true);

            assertThatThrownBy(() -> userApplicationService.register(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("실패 - 저장 시점 중복")
        void registerFailDuplicateUserIdAtSave() {
            RegisterCommand command = RegisterCommand.builder()
                    .userId("testuser")
                    .rawPassword("1Q2w3e4r!")
                    .name("홍길동")
                    .email("test@example.com")
                    .birthDate("19990115")
                    .phone("010-1234-5678")
                    .build();
            when(userRepository.existsByUserId(any(UserId.class))).thenReturn(false);
            when(passwordEncoder.encode("1Q2w3e4r!")).thenReturn("$2a$10$encodedPassword");
            when(userRepository.save(any(User.class))).thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> userApplicationService.register(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.CONFLICT));
        }
    }

    @Nested
    @DisplayName("로그인 ID 중복 검사")
    class DuplicateCheckTest {

        @Test
        @DisplayName("사용 가능한 아이디 - false 반환")
        void checkDuplicateLoginId_available() {
            when(userRepository.existsByUserId(userId)).thenReturn(false);

            boolean result = userApplicationService.checkDuplicateLoginId("testuser");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("이미 사용 중인 아이디 - true 반환")
        void checkDuplicateLoginId_unavailable() {
            when(userRepository.existsByUserId(userId)).thenReturn(true);

            boolean result = userApplicationService.checkDuplicateLoginId("testuser");

            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    class ChangePasswordTest {

        @Test
        @DisplayName("성공")
        void changePasswordSuccess() {
            ChangePasswordCommand command = ChangePasswordCommand.builder()
                    .userId(userId)
                    .newRawPassword("New1234!@")
                    .build();
            User updatedUser = new User(userId, Password.ofEncoded("$2a$10$newEncodedPassword"), name, email, birthDate, phone);

            when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("New1234!@", "$2a$10$encodedPassword")).thenReturn(false);
            when(passwordEncoder.encode("New1234!@")).thenReturn("$2a$10$newEncodedPassword");

            assertThatNoException().isThrownBy(() -> userApplicationService.changePassword(command));
            verify(userRepository).save(updatedUser);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 사용자")
        void changePasswordFailUserNotFound() {
            ChangePasswordCommand command = ChangePasswordCommand.builder()
                    .userId(userId)
                    .newRawPassword("New1234!@")
                    .build();
            when(userRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userApplicationService.changePassword(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.NOT_FOUND));
            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("실패 - 새 비밀번호가 기존과 동일")
        void changePasswordFailSamePassword() {
            ChangePasswordCommand command = ChangePasswordCommand.builder()
                    .userId(userId)
                    .newRawPassword("same")
                    .build();
            when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("same", "$2a$10$encodedPassword")).thenReturn(true);

            assertThatThrownBy(() -> userApplicationService.changePassword(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
            verify(userRepository, never()).save(any(User.class));
        }
    }
}

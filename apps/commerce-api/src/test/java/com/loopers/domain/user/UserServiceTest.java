package com.loopers.domain.user;

import com.loopers.application.user.command.ChangePasswordCommand;
import com.loopers.application.user.command.RegisterCommand;
import com.loopers.domain.user.vo.BirthDate;
import com.loopers.domain.user.vo.Email;
import com.loopers.domain.user.vo.Name;
import com.loopers.domain.user.vo.Password;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private UserId userId;
    private Password password;
    private Name name;
    private Email email;
    private BirthDate birthDate;
    private User user;

    @BeforeEach
    void setUp() {
        userId = new UserId("testuser");
        password = new Password("1Q2w3e4r!");
        name = new Name("홍길동");
        email = new Email("test@example.com");
        birthDate = new BirthDate(LocalDate.of(1999, 1, 15));
        user = new User(userId, password, name, email, birthDate);
    }

    @Nested
    @DisplayName("회원가입")
    class RegisterTest {

        @Test
        @DisplayName("성공")
        void registerSuccess() {
            // given
            RegisterCommand command = RegisterCommand.builder()
                    .userId("testuser")
                    .rawPassword("1Q2w3e4r!")
                    .name("홍길동")
                    .email("test@example.com")
                    .birthDate("19990115")
                    .build();

            when(userRepository.existsByUserId(any(UserId.class))).thenReturn(false);
            when(passwordEncoder.encode("1Q2w3e4r!")).thenReturn("$2a$10$encodedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            // when
            User result = userService.register(command);

            // then
            assertThat(result).isNotNull();
            assertThat(result.id().value()).isEqualTo("testuser");
            assertThat(result.password().value()).isEqualTo("$2a$10$encodedPassword");
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("실패 - 로그인 ID 중복")
        void registerFailDuplicateUserId() {
            // given
            RegisterCommand command = RegisterCommand.builder()
                    .userId("testuser")
                    .rawPassword("1Q2w3e4r!")
                    .name("홍길동")
                    .email("test@example.com")
                    .birthDate("19990115")
                    .build();

            when(userRepository.existsByUserId(any(UserId.class))).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> userService.register(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ex = (CoreException) e;
                        assertThat(ex.getErrorType()).isEqualTo(ErrorType.CONFLICT);
                    });

            verify(userRepository, never()).save(any(User.class));
        }
    }

    @Nested
    @DisplayName("비밀번호 수정")
    class ChangePasswordTest {

        @Test
        @DisplayName("성공")
        void changePasswordSuccess() {
            // given
            User savedUser = new User(userId, Password.ofEncoded("encodedPassword"), name, email, birthDate);
            when(userRepository.findByUserId(userId)).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches("New1234!@", "encodedPassword")).thenReturn(false);
            when(passwordEncoder.encode("New1234!@")).thenReturn("$2a$10$newEncodedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

            ChangePasswordCommand command = ChangePasswordCommand.builder()
                    .userId(userId)
                    .newRawPassword("New1234!@")
                    .birthDate(birthDate)
                    .build();

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> userService.changePassword(command));

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 사용자")
        void changePasswordFailUserNotFound() {
            // given
            when(userRepository.findByUserId(userId)).thenReturn(Optional.empty());

            ChangePasswordCommand command = ChangePasswordCommand.builder()
                    .userId(userId)
                    .newRawPassword("New1234!@")
                    .birthDate(birthDate)
                    .build();

            // when & then
            assertThatThrownBy(() -> userService.changePassword(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ex = (CoreException) e;
                        assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
                    });

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("실패 - 새 비밀번호가 기존과 동일")
        void changePasswordFailSamePassword() {
            // given
            User savedUser = new User(userId, Password.ofEncoded("encodedPassword"), name, email, birthDate);
            when(userRepository.findByUserId(userId)).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches("1Q2w3e4r!", "encodedPassword")).thenReturn(true);

            ChangePasswordCommand command = ChangePasswordCommand.builder()
                    .userId(userId)
                    .newRawPassword("1Q2w3e4r!")
                    .birthDate(birthDate)
                    .build();

            // when & then
            assertThatThrownBy(() -> userService.changePassword(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ex = (CoreException) e;
                        assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
                    });

            verify(userRepository, never()).save(any(User.class));
        }
    }
}

package com.loopers.application.user;

import com.loopers.application.user.command.AuthenticateCommand;
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

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserAuthenticationService userAuthenticationService;

    private UserId userId;
    private User user;

    @BeforeEach
    void setUp() {
        userId = new UserId("testuser");
        user = new User(
                userId,
                Password.ofEncoded("$2a$10$dummyEncodedPasswordForTest"),
                new Name("홍길동"),
                new Email("test@example.com"),
                new BirthDate(LocalDate.of(1999, 1, 15)),
                new Phone("010-1234-5678")
        );
    }

    @Nested
    @DisplayName("인증")
    class AuthenticateTest {

        @Test
        @DisplayName("성공")
        void authenticateSuccess() {
            AuthenticateCommand command = AuthenticateCommand.builder()
                    .userId(userId)
                    .rawPassword("1Q2w3e4r!")
                    .build();
            when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("1Q2w3e4r!", user.password().value())).thenReturn(true);

            User result = userAuthenticationService.authenticate(command);

            assertThat(result.id()).isEqualTo(userId);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 사용자")
        void authenticateFailUserNotFound() {
            AuthenticateCommand command = AuthenticateCommand.builder()
                    .userId(userId)
                    .rawPassword("1Q2w3e4r!")
                    .build();
            when(userRepository.findByUserId(userId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> userAuthenticationService.authenticate(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }

        @Test
        @DisplayName("실패 - 비밀번호 불일치")
        void authenticateFailWrongPassword() {
            AuthenticateCommand command = AuthenticateCommand.builder()
                    .userId(userId)
                    .rawPassword("wrongPassword")
                    .build();
            when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPassword", user.password().value())).thenReturn(false);

            assertThatThrownBy(() -> userAuthenticationService.authenticate(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED));
        }
    }
}

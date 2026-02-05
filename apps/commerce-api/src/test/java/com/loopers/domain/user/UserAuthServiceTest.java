package com.loopers.domain.user;

import com.loopers.application.user.command.AuthenticateCommand;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class UserAuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserAuthService userAuthService;

    private UserId userId;
    private Name name;
    private Email email;
    private BirthDate birthDate;

    @BeforeEach
    void setUp() {
        userId = new UserId("testuser");
        name = new Name("홍길동");
        email = new Email("test@example.com");
        birthDate = new BirthDate(LocalDate.of(1999, 1, 15));
    }

    @Nested
    @DisplayName("인증 (내 정보 조회)")
    class AuthenticateTest {

        @Test
        @DisplayName("성공")
        void authenticateSuccess() {
            // given
            User savedUser = new User(userId, Password.ofEncoded("encodedPassword"), name, email, birthDate);
            when(userRepository.findByUserId(userId)).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches("1Q2w3e4r!", "encodedPassword")).thenReturn(true);

            AuthenticateCommand command = AuthenticateCommand.builder()
                    .userId(userId)
                    .rawPassword("1Q2w3e4r!")
                    .build();

            // when
            User result = userAuthService.authenticate(command);

            // then
            assertThat(result).isNotNull();
            assertThat(result.id()).isEqualTo(userId);
        }

        @Test
        @DisplayName("실패 - 존재하지 않는 사용자")
        void authenticateFailUserNotFound() {
            // given
            when(userRepository.findByUserId(userId)).thenReturn(Optional.empty());

            AuthenticateCommand command = AuthenticateCommand.builder()
                    .userId(userId)
                    .rawPassword("1Q2w3e4r!")
                    .build();

            // when & then
            assertThatThrownBy(() -> userAuthService.authenticate(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ex = (CoreException) e;
                        assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
                    });
        }

        @Test
        @DisplayName("실패 - 비밀번호 불일치")
        void authenticateFailWrongPassword() {
            // given
            User savedUser = new User(userId, Password.ofEncoded("encodedPassword"), name, email, birthDate);
            when(userRepository.findByUserId(userId)).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches("wrongPassword", "encodedPassword")).thenReturn(false);

            AuthenticateCommand command = AuthenticateCommand.builder()
                    .userId(userId)
                    .rawPassword("wrongPassword")
                    .build();

            // when & then
            assertThatThrownBy(() -> userAuthService.authenticate(command))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> {
                        CoreException ex = (CoreException) e;
                        assertThat(ex.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
                    });
        }
    }
}

package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.loopers.support.error.CoreException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@DisplayName("STEP1: UserService Mock 기반 단위 테스트 (Before)")
@ExtendWith(MockitoExtension.class)
class UserServiceMockTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private UserService userService;

    private String loginId;
    private String rawPassword;
    private String name;
    private String birthDate;
    private String email;

    @BeforeEach
    void setUp() {
        loginId = "testuser1";
        rawPassword = "Test1234!@#";
        name = "홍길동";
        birthDate = "19900115";
        email = "test@example.com";
    }

    @DisplayName("회원가입")
    @Nested
    class Signup {

        @Test
        @DisplayName("성공")
        void signup_성공() {
            // arrange
            when(userRepository.findByLoginId(anyString())).thenReturn(Optional.empty());
            when(passwordEncoder.encode("Test1234!@#")).thenReturn("$2a$10$encodedHash");
            when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // act
            UserModel result = userService.signup(loginId, rawPassword, name, birthDate, email);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(loginId);
        }

        @Test
        @DisplayName("중복 아이디면 예외")
        void signup_중복아이디_예외() {
            // arrange
            UserModel existingUser = createTestUser("$2a$10$existingHash");
            when(userRepository.findByLoginId(anyString())).thenReturn(Optional.of(existingUser));

            // act & assert
            assertThatThrownBy(() ->
                userService.signup(loginId, rawPassword, name, birthDate, email)
            ).isInstanceOf(CoreException.class)
             .hasMessageContaining("이미 존재하는 아이디입니다.");
        }
    }

    @DisplayName("비밀번호 변경")
    @Nested
    class ChangePassword {

        @Test
        @DisplayName("성공")
        void changePassword_성공() {
            // arrange
            UserModel existingUser = createTestUser("$2a$10$encodedOldHash");

            when(userRepository.findByLoginId(anyString()))
                .thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("Test1234!@#", "$2a$10$encodedOldHash"))
                .thenReturn(true);
            when(passwordEncoder.matches("NewPass123!@", "$2a$10$encodedOldHash"))
                .thenReturn(false);
            when(passwordEncoder.encode("NewPass123!@"))
                .thenReturn("$2a$10$encodedNewHash");
            when(userRepository.save(any()))
                .thenAnswer(inv -> inv.getArgument(0));

            // act
            userService.changePassword(loginId, "Test1234!@#", "NewPass123!@");

            // assert
            verify(userRepository).save(any());
        }

        @Test
        @DisplayName("현재 비밀번호 불일치면 예외")
        void changePassword_현재비밀번호_불일치() {
            // arrange
            UserModel existingUser = createTestUser("$2a$10$encodedOldHash");

            when(userRepository.findByLoginId(anyString()))
                .thenReturn(Optional.of(existingUser));
            when(passwordEncoder.matches("Wrong123!@#", "$2a$10$encodedOldHash"))
                .thenReturn(false);

            // act & assert
            assertThatThrownBy(() ->
                userService.changePassword(loginId, "Wrong123!@#", "NewPass123!@")
            ).isInstanceOf(CoreException.class)
             .hasMessageContaining("현재 비밀번호가 일치하지 않습니다.");
        }
    }

    // --- 헬퍼 ---

    private UserModel createTestUser(String encodedPassword) {
        return UserModel.create(loginId, encodedPassword, name, LocalDate.of(1990, 1, 15), email);
    }
}

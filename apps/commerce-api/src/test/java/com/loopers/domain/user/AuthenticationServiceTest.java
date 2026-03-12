package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
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

@DisplayName("AuthenticationService 단위 테스트")
@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthenticationService authenticationService;

    private UserModel testUser;
    private String validLoginId;
    private String validPassword;
    private String encodedPassword;

    @BeforeEach
    void setUp() {
        validLoginId = "testuser123";
        validPassword = "Test1234!@#";
        encodedPassword = "$2a$10$encodedPasswordHash";

        testUser = UserModel.create(
                validLoginId, encodedPassword,
                "홍길동", LocalDate.of(1990, 1, 15), "test@example.com"
        );
    }

    @DisplayName("authenticate 메서드는")
    @Nested
    class Authenticate {

        @Test
        @DisplayName("올바른 로그인 ID와 비밀번호로 인증하면 사용자 정보를 반환한다")
        void authenticate_should_return_user_when_credentials_are_correct() {
            // arrange
            when(userRepository.findByLoginId(anyString())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(validPassword, encodedPassword)).thenReturn(true);

            // act
            UserModel result = authenticationService.authenticate(validLoginId, validPassword);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getLoginId()).isEqualTo(validLoginId);
        }

        @Test
        @DisplayName("존재하지 않는 로그인 ID로 인증하면 UNAUTHORIZED 예외를 던진다")
        void authenticate_should_throw_exception_when_user_not_found() {
            // arrange
            when(userRepository.findByLoginId(anyString())).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> authenticationService.authenticate(validLoginId, validPassword))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorType.UNAUTHORIZED)
                .hasMessageContaining("로그인 ID 또는 비밀번호가 일치하지 않습니다.");
        }

        @Test
        @DisplayName("잘못된 비밀번호로 인증하면 UNAUTHORIZED 예외를 던진다")
        void authenticate_should_throw_exception_when_password_is_incorrect() {
            // arrange
            when(userRepository.findByLoginId(anyString())).thenReturn(Optional.of(testUser));
            String wrongPassword = "Wrong1234!@#";
            when(passwordEncoder.matches(wrongPassword, encodedPassword)).thenReturn(false);

            // act & assert
            assertThatThrownBy(() -> authenticationService.authenticate(validLoginId, wrongPassword))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorType.UNAUTHORIZED)
                .hasMessageContaining("로그인 ID 또는 비밀번호가 일치하지 않습니다.");
        }

        @Test
        @DisplayName("비밀번호가 null이면 UNAUTHORIZED 예외를 던진다")
        void authenticate_should_throw_exception_when_password_is_null() {
            // arrange
            when(userRepository.findByLoginId(anyString())).thenReturn(Optional.of(testUser));
            when(passwordEncoder.matches(null, encodedPassword)).thenReturn(false);

            // act & assert
            assertThatThrownBy(() -> authenticationService.authenticate(validLoginId, null))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorType.UNAUTHORIZED);
        }
    }
}

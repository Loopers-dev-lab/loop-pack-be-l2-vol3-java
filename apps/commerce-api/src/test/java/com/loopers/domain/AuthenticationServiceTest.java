package com.loopers.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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

    @InjectMocks
    private AuthenticationService authenticationService;

    private UserModel testUser;
    private String validLoginId;
    private String validPassword;

    @BeforeEach
    void setUp() {
        validLoginId = "testuser123";
        validPassword = "Test1234!@#";

        testUser = new UserModel(
            new LoginId(validLoginId),
            new Password(validPassword),
            new Name("홍길동"),
            new BirthDate(LocalDate.of(1990, 1, 15)),
            new Email("test@example.com")
        );
    }

    @DisplayName("authenticate 메서드는")
    @Nested
    class Authenticate {

        @Test
        @DisplayName("올바른 로그인 ID와 비밀번호로 인증하면 사용자 정보를 반환한다")
        void authenticate_should_return_user_when_credentials_are_correct() {
            // arrange
            when(userRepository.find(any(LoginId.class))).thenReturn(Optional.of(testUser));

            // act
            UserModel result = authenticationService.authenticate(validLoginId, validPassword);

            // assert
            assertThat(result).isNotNull();
            assertThat(result.getLoginId().getValue()).isEqualTo(validLoginId);
        }

        @Test
        @DisplayName("존재하지 않는 로그인 ID로 인증하면 UNAUTHORIZED 예외를 던진다")
        void authenticate_should_throw_exception_when_user_not_found() {
            // arrange
            when(userRepository.find(any(LoginId.class))).thenReturn(Optional.empty());

            // act & assert
            assertThatThrownBy(() -> authenticationService.authenticate(validLoginId, validPassword))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.UNAUTHORIZED)
                .hasMessageContaining("로그인 ID 또는 비밀번호가 일치하지 않습니다.");
        }

        @Test
        @DisplayName("잘못된 비밀번호로 인증하면 UNAUTHORIZED 예외를 던진다")
        void authenticate_should_throw_exception_when_password_is_incorrect() {
            // arrange
            when(userRepository.find(any(LoginId.class))).thenReturn(Optional.of(testUser));
            String wrongPassword = "Wrong1234!@#";

            // act & assert
            assertThatThrownBy(() -> authenticationService.authenticate(validLoginId, wrongPassword))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.UNAUTHORIZED)
                .hasMessageContaining("로그인 ID 또는 비밀번호가 일치하지 않습니다.");
        }

        @Test
        @DisplayName("비밀번호가 null이면 BAD_REQUEST 예외를 던진다")
        void authenticate_should_throw_exception_when_password_is_null() {
            // act & assert
            assertThatThrownBy(() -> authenticationService.authenticate(validLoginId, null))
                .isInstanceOf(CoreException.class)
                .hasFieldOrPropertyWithValue("errorType", ErrorType.BAD_REQUEST);
        }
    }
}

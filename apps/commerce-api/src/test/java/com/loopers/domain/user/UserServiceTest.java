package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceTest {

    UserService userService;
    PasswordEncoder passwordEncoder;
    UserRepository userRepository;

    @BeforeEach
    void beforeEach() {
        passwordEncoder = mock(PasswordEncoder.class);
        userRepository = mock(UserRepository.class);
        userService = new UserService(passwordEncoder, userRepository);
    }

    @DisplayName("회원가입 시, ")
    @Nested
    class Signup {
        @Test
        void 중복된_로그인ID면_CONFLICT를_던진다() {
            // given
            String loginId = "loopers123";
            String password = "loopers123!@";
            String name = "루퍼스";
            LocalDate birthDate = LocalDate.of(1996, 11, 22);
            String email = "test@loopers.im";

            when(userRepository.findByLoginId(LoginId.from(loginId))).thenReturn(Optional.of(mock(User.class)));


            // when-then
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.signup(loginId, password, name, birthDate, email);
            });
            assertThat(result.getErrorType()).isEqualTo(ErrorType.CONFLICT);
        }

        @Test
        void 생년월일이_비밀번호에_포함되면_BAD_REQUEST를_던진다() {
            // given
            String loginId = "loopers123";
            String password = "lo19961122@";
            String name = "루퍼스";
            LocalDate birthDate = LocalDate.of(1996, 11, 22);
            String email = "test@loopers.im";

            when(userRepository.findByLoginId(LoginId.from(loginId))).thenReturn(Optional.empty());

            // when-then
            CoreException result = assertThrows(CoreException.class, () -> {
                userService.signup(loginId, password, name, birthDate, email);
            });
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        void 정상적으로_되면_회원객체를_생성해서_반환() {
            // given
            String loginId = "loopers123";
            String password = "loopers123!@";
            String name = "루퍼스";
            LocalDate birthDate = LocalDate.of(1996, 11, 22);
            String email = "test@loopers.im";

            when(userRepository.findByLoginId(LoginId.from(loginId))).thenReturn(Optional.empty());

            // when
            LoginId returnLoginId = userService.signup(loginId, password, name, birthDate, email);

            // then
            assertThat(returnLoginId).isEqualTo(LoginId.from(loginId));
        }
    }

    @DisplayName("내 정보 조회 시, ")
    @Nested
    class GetMyInfo {

        @Test
        void 존재하지_않는_loginId면_NOT_FOUND를_던진다() {
            // Arrange
            when(userRepository.findByLoginId(LoginId.from("loopers123"))).thenReturn(Optional.empty());

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.getMyInfo("loopers123", "loopers123!@");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        void 비밀번호가_불일치하면_UNAUTHORIZED를_던진다() {
            // Arrange
            User user = User.create(
                    LoginId.from("loopers123"),
                    Password.of("loopers123!@", passwordEncoder),
                    Name.from("루퍼스"),
                    BirthDate.from(LocalDate.of(1996, 11, 22)),
                    Email.from("test@loopers.im")
            );
            when(userRepository.findByLoginId(LoginId.from("loopers123"))).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("wrongPass123", user.password().asString())).thenReturn(false);

            // Act & Assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                userService.getMyInfo("loopers123", "wrongPass123");
            });
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }

        @Test
        void 정상_인증이면_UserInfo를_반환한다() {
            // Arrange
            when(passwordEncoder.encode("loopers123!@")).thenReturn("encoded");
            User user = User.create(
                    LoginId.from("loopers123"),
                    Password.of("loopers123!@", passwordEncoder),
                    Name.from("루퍼스"),
                    BirthDate.from(LocalDate.of(1996, 11, 22)),
                    Email.from("test@loopers.im")
            );
            when(userRepository.findByLoginId(LoginId.from("loopers123"))).thenReturn(Optional.of(user));
            when(passwordEncoder.matches("loopers123!@", "encoded")).thenReturn(true);

            // Act
            UserInfo result = userService.getMyInfo("loopers123", "loopers123!@");

            // Assert
            assertThat(result.loginId()).isEqualTo("loopers123");
            assertThat(result.maskedName()).isEqualTo("루퍼*");
            assertThat(result.birthDate()).isEqualTo(LocalDate.of(1996, 11, 22));
            assertThat(result.email()).isEqualTo("test@loopers.im");
        }
    }

}

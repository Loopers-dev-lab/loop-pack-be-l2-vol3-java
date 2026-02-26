package com.loopers.application.user;

import com.loopers.application.user.RegisterUseCase.RegisterCommand;
import com.loopers.domain.model.user.*;
import com.loopers.domain.repository.UserRepository;
import com.loopers.domain.service.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserService service;

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 5, 15);

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new UserService(userRepository, passwordEncoder);
    }

    @Nested
    @DisplayName("회원가입")
    class Register {

        @Test
        @DisplayName("회원가입 성공")
        void register_success() {
            // given
            String loginId = "test1234";
            String name = "홍길동";
            String rawPassword = "Password1!";
            String email = "test@example.com";

            when(passwordEncoder.encrypt(anyString())).thenReturn("encoded_password");

            // when & then
            var command = new RegisterCommand(loginId, name, rawPassword, BIRTHDAY, email);
            assertThatNoException()
                    .isThrownBy(() -> service.register(command));

            verify(passwordEncoder).encrypt(rawPassword);
            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("중복된 ID로 가입시 예외")
        void register_fail_duplicated_id() {
            // given
            String duplicatedId = "test1234";
            String name = "홍길동";
            String rawPassword = "Password1!";
            String email = "test@example.com";

            when(passwordEncoder.encrypt(anyString())).thenReturn("encoded_password");
            doThrow(new DataIntegrityViolationException("Duplicate entry"))
                    .when(userRepository).save(any(User.class));

            // when & then
            var command = new RegisterCommand(duplicatedId, name, rawPassword, BIRTHDAY, email);
            assertThatThrownBy(() -> service.register(command))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이미 사용중인 ID");
        }
    }

    @Nested
    @DisplayName("비밀번호 변경")
    class PasswordUpdate {

        @Test
        @DisplayName("비밀번호 변경 성공")
        void updatePassword_success() {
            // given
            UserId userId = UserId.of("test1234");
            User user = createUser(userId, "encoded_current");
            String currentRawPassword = "Current1!";
            String newRawPassword = "NewPass1!";

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(currentRawPassword, "encoded_current")).thenReturn(true);
            when(passwordEncoder.matches(newRawPassword, "encoded_current")).thenReturn(false);
            when(passwordEncoder.encrypt(newRawPassword)).thenReturn("encoded_new");

            // when & then
            assertThatNoException()
                    .isThrownBy(() -> service.updatePassword(userId, currentRawPassword, newRawPassword));

            verify(userRepository).save(any(User.class));
        }

        @Test
        @DisplayName("현재 비밀번호 불일치시 예외")
        void updatePassword_fail_wrong_current() {
            // given
            UserId userId = UserId.of("test1234");
            User user = createUser(userId, "encoded_current");
            String wrongRawPassword = "WrongPw1!";
            String newRawPassword = "NewPass1!";

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(wrongRawPassword, "encoded_current")).thenReturn(false);

            // when & then
            assertThatThrownBy(() -> service.updatePassword(userId, wrongRawPassword, newRawPassword))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("현재 비밀번호가 일치하지 않습니다");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("새 비밀번호가 현재와 동일하면 예외")
        void updatePassword_fail_same_password() {
            // given
            UserId userId = UserId.of("test1234");
            User user = createUser(userId, "encoded_current");
            String currentRawPassword = "Current1!";
            String sameRawPassword = "Current1!";

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(currentRawPassword, "encoded_current")).thenReturn(true);

            // when & then
            assertThatThrownBy(() -> service.updatePassword(userId, currentRawPassword, sameRawPassword))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("현재 비밀번호는 사용할 수 없습니다");

            verify(userRepository, never()).save(any(User.class));
        }

        @Test
        @DisplayName("존재하지 않는 사용자면 예외")
        void updatePassword_fail_user_not_found() {
            // given
            UserId userId = UserId.of("notexist");

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.updatePassword(userId, "Current1!", "NewPass1!"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }
    }

    @Nested
    @DisplayName("내 정보 조회")
    class UserQuery {

        @Test
        @DisplayName("내 정보 조회 성공")
        void getUserInfo_success() {
            // given
            UserId userId = UserId.of("test1234");
            User user = User.reconstitute(new UserData(
                    1L,
                    userId,
                    UserName.of("홍길동"),
                    "encoded_password",
                    Birthday.of(BIRTHDAY),
                    Email.of("test@example.com"),
                    0,
                    LocalDateTime.now()
            ));

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            var result = service.getUserInfo(userId);

            // then
            assertThat(result.loginId()).isEqualTo("test1234");
            assertThat(result.maskedName()).isEqualTo("홍길*");
            assertThat(result.birthday()).isEqualTo(BIRTHDAY);
            assertThat(result.email()).isEqualTo("test@example.com");
        }

        @Test
        @DisplayName("이름 마스킹 - 2자")
        void getUserInfo_maskedName_2chars() {
            // given
            UserId userId = UserId.of("test1234");
            User user = User.reconstitute(new UserData(
                    1L,
                    userId,
                    UserName.of("홍길"),
                    "encoded_password",
                    Birthday.of(BIRTHDAY),
                    Email.of("test@example.com"),
                    0,
                    LocalDateTime.now()
            ));

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));

            // when
            var result = service.getUserInfo(userId);

            // then
            assertThat(result.maskedName()).isEqualTo("홍*");
        }

        @Test
        @DisplayName("존재하지 않는 사용자 조회시 예외")
        void getUserInfo_fail_not_found() {
            // given
            UserId userId = UserId.of("notexist");

            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> service.getUserInfo(userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("사용자를 찾을 수 없습니다");
        }

        @Test
        @DisplayName("이름은 최소 2자 이상이어야 한다")
        void userName_fail_lessThan2chars() {
            assertThatThrownBy(() -> UserName.of("홍"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("2~20자");
        }
    }

    private User createUser(UserId userId, String encodedPassword) {
        return User.reconstitute(new UserData(
                1L,
                userId,
                UserName.of("홍길동"),
                encodedPassword,
                Birthday.of(BIRTHDAY),
                Email.of("test@example.com"),
                0,
                LocalDateTime.now()
        ));
    }
}

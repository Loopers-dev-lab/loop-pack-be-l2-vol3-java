package com.loopers.application.user;

import com.loopers.domain.model.user.*;
import com.loopers.domain.repository.UserRepository;
import com.loopers.domain.service.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthenticationServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationService service;

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 5, 15);

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AuthenticationService(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("인증 성공")
    void authenticate_success() {
        // given
        UserId userId = UserId.of("test1234");
        String rawPassword = "Password1!";
        String encodedPassword = "encoded_password";
        User user = createUser(userId, encodedPassword);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

        // when & then
        assertThatNoException()
                .isThrownBy(() -> service.authenticate(userId, rawPassword));

        verify(userRepository).findById(userId);
        verify(passwordEncoder).matches(rawPassword, encodedPassword);
    }

    @Test
    @DisplayName("존재하지 않는 사용자 인증 실패")
    void authenticate_fail_userNotFound() {
        // given
        UserId userId = UserId.of("notexist");

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> service.authenticate(userId, "password"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("아이디 또는 비밀번호가 올바르지 않습니다");
    }

    @Test
    @DisplayName("비밀번호 불일치 인증 실패")
    void authenticate_fail_passwordMismatch() {
        // given
        UserId userId = UserId.of("test1234");
        String wrongPassword = "WrongPassword1!";
        String encodedPassword = "encoded_password";
        User user = createUser(userId, encodedPassword);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(wrongPassword, encodedPassword)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> service.authenticate(userId, wrongPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("아이디 또는 비밀번호가 올바르지 않습니다");
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

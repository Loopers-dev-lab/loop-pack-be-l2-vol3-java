package com.loopers.application.service;

import com.loopers.domain.model.*;
import com.loopers.domain.repository.UserRepository;
import com.loopers.domain.service.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthenticationServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private AuthenticationService service;

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

        User user = User.reconstitute(
                1L,
                userId,
                UserName.of("홍길동"),
                encodedPassword,
                Birthday.of(LocalDate.of(1990, 5, 15)),
                Email.of("test@example.com"),
                WrongPasswordCount.init(),
                LocalDateTime.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(rawPassword, encodedPassword)).thenReturn(true);

        // when & then - 예외가 발생하지 않으면 성공
        service.authenticate(userId, rawPassword);

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
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }

    @Test
    @DisplayName("비밀번호 불일치 인증 실패")
    void authenticate_fail_passwordMismatch() {
        // given
        UserId userId = UserId.of("test1234");
        String wrongPassword = "WrongPassword1!";
        String encodedPassword = "encoded_password";

        User user = User.reconstitute(
                1L,
                userId,
                UserName.of("홍길동"),
                encodedPassword,
                Birthday.of(LocalDate.of(1990, 5, 15)),
                Email.of("test@example.com"),
                WrongPasswordCount.init(),
                LocalDateTime.now()
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(wrongPassword, encodedPassword)).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> service.authenticate(userId, wrongPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비밀번호가 일치하지 않습니다");
    }
}
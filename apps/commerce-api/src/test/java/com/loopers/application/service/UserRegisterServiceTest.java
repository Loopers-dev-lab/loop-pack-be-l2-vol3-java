package com.loopers.application.service;

import com.loopers.domain.model.User;
import com.loopers.domain.repository.UserRepository;
import com.loopers.domain.service.PasswordEncoder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class UserRegisterServiceTest {

    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private UserRegisterService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new UserRegisterService(userRepository, passwordEncoder);
    }

    @Test
    @DisplayName("회원가입 성공")
    void register_success() {
        // given
        String loginId = "test1234";
        String name = "홍길동";
        String rawPassword = "Password1!";
        LocalDate birthday = LocalDate.of(1990, 5, 15);
        String email = "test@example.com";

        when(passwordEncoder.encrypt(anyString())).thenReturn("encoded_password");

        // when & then
        assertThatNoException()
                .isThrownBy(() -> service.register(loginId, name, rawPassword, birthday, email));

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
        LocalDate birthday = LocalDate.of(1990, 5, 15);
        String email = "test@example.com";

        when(passwordEncoder.encrypt(anyString())).thenReturn("encoded_password");
        doThrow(new DataIntegrityViolationException("Duplicate entry"))
                .when(userRepository).save(any(User.class));

        // when & then
        assertThatThrownBy(() -> service.register(duplicatedId, name, rawPassword, birthday, email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 사용중인 ID");
    }
}

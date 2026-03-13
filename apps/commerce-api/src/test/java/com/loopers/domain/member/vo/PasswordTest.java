package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordTest {

    private final PasswordEncoder encoder = new BCryptPasswordEncoder();

    @DisplayName("유효한 비밀번호로 Password를 생성할 수 있다")
    @Test
    void create_withValidPassword_succeeds() {
        Password password = Password.create("Password1!", LocalDate.of(1990, 1, 15), encoder);
        assertThat(password.encoded()).isNotBlank();
    }

    @DisplayName("비밀번호가 형식에 맞지 않으면 생성에 실패한다")
    @Test
    void create_withInvalidFormat_throwsException() {
        assertThatThrownBy(() -> Password.create("short", LocalDate.of(1990, 1, 15), encoder))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("비밀번호에 생년월일이 포함되면 생성에 실패한다")
    @Test
    void create_withBirthDate_throwsException() {
        assertThatThrownBy(() -> Password.create("Pass19900115!", LocalDate.of(1990, 1, 15), encoder))
            .isInstanceOf(CoreException.class);
    }

    @DisplayName("matches로 평문 비밀번호를 검증할 수 있다")
    @Test
    void matches_withCorrectPassword_returnsTrue() {
        Password password = Password.create("Password1!", LocalDate.of(1990, 1, 15), encoder);
        assertThat(password.matches("Password1!", encoder)).isTrue();
    }

    @DisplayName("matches로 틀린 비밀번호를 거부할 수 있다")
    @Test
    void matches_withWrongPassword_returnsFalse() {
        Password password = Password.create("Password1!", LocalDate.of(1990, 1, 15), encoder);
        assertThat(password.matches("WrongPass1!", encoder)).isFalse();
    }

    @DisplayName("encoded가 null이면 생성에 실패한다")
    @Test
    void constructor_withNull_throwsException() {
        assertThatThrownBy(() -> new Password(null))
            .isInstanceOf(CoreException.class);
    }
}

package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.loopers.support.error.CoreException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserModelTest {

    @DisplayName("UserModel 생성 테스트")
    @Test
    void success_create_userModel() {
        String loginId = "user123";
        String encodedPw = "encoded_hash";
        LocalDate birthDate = LocalDate.of(1991, 12, 3);
        String name = "김용권";
        String email = "yk@google.com";

        UserModel user = UserModel.create(loginId, encodedPw, birthDate, name, email);

        assertThat(user.getLoginId()).isEqualTo(loginId);
        assertThat(user.getPassword()).isEqualTo(encodedPw);
        assertThat(user.getBirthDate()).isEqualTo(birthDate);
        assertThat(user.getName()).isEqualTo(name);
        assertThat(user.getEmail()).isEqualTo(email);
    }

    @DisplayName("loginId가 null이면 guard에서 예외가 발생한다.")
    @Test
    void guard_fail_when_loginId_is_null() {
        UserModel user = UserModel.create(null, "encoded_hash", LocalDate.of(1991, 12, 3), "김용권", "yk@google.com");

        assertThatThrownBy(user::guard).isInstanceOf(CoreException.class);
    }

    @DisplayName("password가 null이면 guard에서 예외가 발생한다.")
    @Test
    void guard_fail_when_password_is_null() {
        UserModel user = UserModel.create("user123", null, LocalDate.of(1991, 12, 3), "김용권", "yk@google.com");

        assertThatThrownBy(user::guard).isInstanceOf(CoreException.class);
    }

    @DisplayName("birthDate가 null이면 guard에서 예외가 발생한다.")
    @Test
    void guard_fail_when_birthDate_is_null() {
        UserModel user = UserModel.create("user123", "encoded_hash", null, "김용권", "yk@google.com");

        assertThatThrownBy(user::guard).isInstanceOf(CoreException.class);
    }

    @DisplayName("name이 null이면 guard에서 예외가 발생한다.")
    @Test
    void guard_fail_when_name_is_null() {
        UserModel user = UserModel.create("user123", "encoded_hash", LocalDate.of(1991, 12, 3), null, "yk@google.com");

        assertThatThrownBy(user::guard).isInstanceOf(CoreException.class);
    }

    @DisplayName("email이 null이면 guard에서 예외가 발생한다.")
    @Test
    void guard_fail_when_email_is_null() {
        UserModel user = UserModel.create("user123", "encoded_hash", LocalDate.of(1991, 12, 3), "김용권", null);

        assertThatThrownBy(user::guard).isInstanceOf(CoreException.class);
    }
}

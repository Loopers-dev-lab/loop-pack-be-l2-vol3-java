package com.loopers.domain.model.user;

import com.loopers.domain.service.PasswordEncoder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserTest {

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 5, 15);

    @Test
    @DisplayName("비밀번호 변경 성공")
    void changePassword_success() {
        User user = createUser("encoded_current");
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        when(encoder.matches("Current1!", "encoded_current")).thenReturn(true);
        when(encoder.matches("NewPass1!", "encoded_current")).thenReturn(false);
        when(encoder.encrypt("NewPass1!")).thenReturn("encoded_new");

        User updated = user.changePassword("Current1!", "NewPass1!", encoder);

        assertThat(updated.getEncodedPassword()).isEqualTo("encoded_new");
    }

    @Test
    @DisplayName("현재 비밀번호 불일치시 예외")
    void changePassword_fail_wrongCurrent() {
        User user = createUser("encoded_current");
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        when(encoder.matches("WrongPw1!", "encoded_current")).thenReturn(false);

        assertThatThrownBy(() -> user.changePassword("WrongPw1!", "NewPass1!", encoder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 비밀번호가 일치하지 않습니다");
    }

    @Test
    @DisplayName("새 비밀번호가 현재와 동일하면 예외")
    void changePassword_fail_samePassword() {
        User user = createUser("encoded_current");
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        when(encoder.matches("Current1!", "encoded_current")).thenReturn(true);
        when(encoder.matches("Current1!", "encoded_current")).thenReturn(true);

        assertThatThrownBy(() -> user.changePassword("Current1!", "Current1!", encoder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 비밀번호는 사용할 수 없습니다");
    }

    private User createUser(String encodedPassword) {
        return User.reconstitute(new UserData(
                1L,
                UserId.of("test1234"),
                UserName.of("홍길동"),
                encodedPassword,
                Birthday.of(BIRTHDAY),
                Email.of("test@example.com"),
                0,
                LocalDateTime.now()
        ));
    }
}

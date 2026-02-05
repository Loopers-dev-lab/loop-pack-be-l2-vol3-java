package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserTest {

    @Test
    void 로그인ID_비밀번호_이름_생년월일_이메일이_모두_주어지면_정상적으로_생성() {
        // given
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode("loopers123")).thenReturn("encrypted");
        when(encoder.matches("loopers123", "encrypted")).thenReturn(true);

        LoginId loginId = LoginId.from("loopers");
        Password password = Password.of("loopers123", encoder);
        Name name = Name.from("루퍼스");
        BirthDate birthDate = BirthDate.from(LocalDate.of(1996, 11, 22));
        Email email = Email.from("tester@loopers.im");

        // when
        User user = User.create(loginId, password, name, birthDate, email);

        // then
        assertAll(
                () -> assertThat(user.loginId()).isEqualTo(loginId),
                () -> assertThat(user.password().matches("loopers123", encoder)).isTrue(),
                () -> assertThat(user.name()).isEqualTo(name),
                () -> assertThat(user.birthDate()).isEqualTo(birthDate),
                () -> assertThat(user.email()).isEqualTo(email)
        );
    }

    @Test
    void 로그인ID_비밀번호_이름_생년월일_이메일_중_하나라도_null이면_BAD_REQUEST를_던진다() {
        // given
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        LoginId loginId = LoginId.from("loopers");
        Password password = Password.of("loopers123", encoder);
        Name name = null;
        BirthDate birthDate = BirthDate.from(LocalDate.of(1996, 11, 22));
        Email email = Email.from("tester@loopers.im");

        // when-then
        CoreException result = assertThrows(CoreException.class, () -> {
            User.create(loginId, password, name, birthDate, email);
        });
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }
}

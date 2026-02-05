package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordTest {

    @ParameterizedTest
    @ValueSource(strings = {"1234567", "12345678123456789"})
    void 비밀번호가_8자미만_16자초과이면_BAD_REQUEST를_던진다(String value) {
        // given
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        // when-then
        CoreException result = assertThrows(CoreException.class, () -> {
            Password.of(value, encoder);
        });
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    void 비밀번호에_한글이_포함되면_BAD_REQUEST를_던진다() {
        // given
        String value = "비밀번호486";
        PasswordEncoder encoder = mock(PasswordEncoder.class);

        // when-then
        CoreException result = assertThrows(CoreException.class, () -> {
            Password.of(value, encoder);
        });
        assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }


    @Test
    void 유효한_비밀번호이면_암호화해서_객체생성_성공() {
        // given
        String value = "loopers123";
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.encode(value)).thenReturn("encrypted");

        // when
        Password password = Password.of(value, encoder);

        // then
        assertThat(password.asString()).isEqualTo("encrypted");
    }
}

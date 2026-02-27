package com.loopers.domain.member.vo;

import com.loopers.support.error.CoreException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PasswordTest {
    @Test
    @DisplayName("비밀번호 검증 성공")
    public void testPasswordSuccess() {
        Assertions.assertThatNoException().isThrownBy(() -> new Password("1Q2w3e4r!"));
    }

    @Test
    @DisplayName("대문자 없음")
    public void throwsExceptionWhenNoUppercase() {
        Assertions.assertThatThrownBy(() -> new Password("1q2w3e4r!")).isInstanceOf(CoreException.class)
            .hasMessage("대문자를 포함해야 합니다");
    }

    @Test
    @DisplayName("소문자 없음")
    public void throwsExceptionWhenNoLowercase() {
        Assertions.assertThatThrownBy(() -> new Password("1Q2W3E4R!")).isInstanceOf(CoreException.class)
            .hasMessage("소문자를 포함해야 합니다");
    }

    @Test
    @DisplayName("숫자 없음")
    public void throwsExceptionWhenNoDigit() {
        Assertions.assertThatThrownBy(() -> new Password("qQwweerr!")).isInstanceOf(CoreException.class)
            .hasMessage("숫자를 포함해야 합니다");
    }

    @Test
    @DisplayName("특수문자 없음")
    public void throwsExceptionWhenNoSpecialChar() {
        Assertions.assertThatThrownBy(() -> new Password("qQwweerrr1")).isInstanceOf(CoreException.class)
            .hasMessage("특수문자를 포함해야 합니다");
    }

    @Test
    @DisplayName("길이 미달")
    public void throwsExceptionWhenTooShort() {
        Assertions.assertThatThrownBy(() -> new Password("1Q2w3e!")).isInstanceOf(CoreException.class)
            .hasMessage("비밀번호는 8자 이상이어야 합니다");
    }

    @Test
    @DisplayName("길이 경계값 - 8자 성공")
    public void passwordExactly8Chars() {
        Assertions.assertThatNoException()
            .isThrownBy(() -> new Password("1Q2w3e4!"));
    }

    @Test
    @DisplayName("길이 초과")
    public void throwsExceptionWhenTooLong() {
        Assertions.assertThatThrownBy(() -> new Password("1Q2w3e4r!12345678")).isInstanceOf(CoreException.class)
            .hasMessage("비밀번호는 16자 이하여야 합니다");
    }

    @Test
    @DisplayName("길이 경계값 - 16자 성공")
    public void passwordExactly16Chars() {
        Assertions.assertThatNoException()
            .isThrownBy(() -> new Password("1Q2w3e4r!1234567"));
    }

    @Test
    @DisplayName("BCrypt $2y$ prefix를 인코딩 비밀번호로 인식한다")
    public void recognizesBcrypt2yPrefixAsEncoded() {
        // given
        Password encoded = Password.ofEncoded("$2y$10$dummyEncodedPasswordForTest");

        // when & then
        Assertions.assertThat(encoded.isEncoded()).isTrue();
    }
}

package com.loopers.interfaces.api.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserDtoToStringTest {

    @Test
    @DisplayName("회원가입 요청 toString은 비밀번호를 마스킹한다")
    void registerRequestToStringMasksPassword() {
        UserDto.RegisterRequest request = new UserDto.RegisterRequest(
                "testuser1",
                "Password1!",
                "홍길동",
                "19900101",
                "test@example.com",
                "010-1234-5678"
        );

        String result = request.toString();

        assertThat(result).contains("password=***");
        assertThat(result).doesNotContain("Password1!");
    }

    @Test
    @DisplayName("회원가입 요청 toString은 전화번호를 마스킹한다")
    void registerRequestToStringMasksPhone() {
        UserDto.RegisterRequest request = new UserDto.RegisterRequest(
                "testuser1",
                "Password1!",
                "홍길동",
                "19900101",
                "test@example.com",
                "010-1234-5678"
        );

        String result = request.toString();

        assertThat(result).contains("phone=010-****-5678");
        assertThat(result).doesNotContain("010-1234-5678");
    }

    @Test
    @DisplayName("비밀번호 변경 요청 toString은 새 비밀번호를 마스킹한다")
    void changePasswordRequestToStringMasksPassword() {
        UserDto.ChangePasswordRequest request = new UserDto.ChangePasswordRequest("NewPassword1!");

        String result = request.toString();

        assertThat(result).contains("newPassword=***");
        assertThat(result).doesNotContain("NewPassword1!");
    }
}

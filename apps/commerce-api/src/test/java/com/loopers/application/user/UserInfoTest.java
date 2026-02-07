package com.loopers.application.user;

import com.loopers.domain.user.UserModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserInfoTest {

    @DisplayName("이름의 마지막 글자는 *로 마스킹된다.")
    @Test
    void masksLastCharacterOfName() {
        // arrange
        String loginId = "testuser";
        String encodedPassword = "encodedPassword";
        String name = "홍길동";
        LocalDate birthDate = LocalDate.of(1990, 1, 15);
        String email = "test@example.com";

        UserModel user = UserModel.createWithEncodedPassword(loginId, encodedPassword, name, birthDate, email);

        // act
        UserInfo result = UserInfo.from(user);

        // assert
        assertThat(result.name()).isEqualTo("홍길*");
    }

    @DisplayName("이름이 한 글자인 경우, *로 마스킹된다.")
    @Test
    void masksSingleCharacterName() {
        // arrange
        String loginId = "testuser";
        String encodedPassword = "encodedPassword";
        String name = "홍";
        LocalDate birthDate = LocalDate.of(1990, 1, 15);
        String email = "test@example.com";

        UserModel user = UserModel.createWithEncodedPassword(loginId, encodedPassword, name, birthDate, email);

        // act
        UserInfo result = UserInfo.from(user);

        // assert
        assertThat(result.name()).isEqualTo("*");
    }

    @DisplayName("UserInfo는 로그인ID, 마스킹된 이름, 생년월일, 이메일을 포함한다.")
    @Test
    void containsAllRequiredFields() {
        // arrange
        String loginId = "testuser";
        String encodedPassword = "encodedPassword";
        String name = "홍길동";
        LocalDate birthDate = LocalDate.of(1990, 1, 15);
        String email = "test@example.com";

        UserModel user = UserModel.createWithEncodedPassword(loginId, encodedPassword, name, birthDate, email);

        // act
        UserInfo result = UserInfo.from(user);

        // assert
        assertThat(result.loginId()).isEqualTo(loginId);
        assertThat(result.name()).isEqualTo("홍길*");
        assertThat(result.birthDate()).isEqualTo(birthDate);
        assertThat(result.email()).isEqualTo(email);
    }
}

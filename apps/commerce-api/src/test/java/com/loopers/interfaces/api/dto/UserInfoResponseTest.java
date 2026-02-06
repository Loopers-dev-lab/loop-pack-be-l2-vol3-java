package com.loopers.interfaces.api.dto;

import com.loopers.application.UserQueryUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class UserInfoResponseTest {

    @Test
    @DisplayName("생년월일이 yyyyMMdd 형식으로 포맷팅된다")
    void from_formatsBirthdayCorrectly() {
        // given
        var userInfo = new UserQueryUseCase.UserInfoResponse(
                "test1234",
                "홍길*",
                LocalDate.of(1990, 5, 15),
                "test@example.com"
        );

        // when
        var response = UserInfoResponse.from(userInfo);

        // then
        assertThat(response.birthday()).isEqualTo("19900515");
    }

    @Test
    @DisplayName("한 자리 월/일은 앞에 0이 붙는다")
    void from_formatsBirthdayWithLeadingZeros() {
        // given
        var userInfo = new UserQueryUseCase.UserInfoResponse(
                "test1234",
                "홍길*",
                LocalDate.of(2000, 1, 5),
                "test@example.com"
        );

        // when
        var response = UserInfoResponse.from(userInfo);

        // then
        assertThat(response.birthday()).isEqualTo("20000105");
    }

    @Test
    @DisplayName("모든 필드가 올바르게 매핑된다")
    void from_mapsAllFieldsCorrectly() {
        // given
        var userInfo = new UserQueryUseCase.UserInfoResponse(
                "user123",
                "김철*",
                LocalDate.of(1985, 12, 25),
                "kim@example.com"
        );

        // when
        var response = UserInfoResponse.from(userInfo);

        // then
        assertThat(response.loginId()).isEqualTo("user123");
        assertThat(response.name()).isEqualTo("김철*");
        assertThat(response.birthday()).isEqualTo("19851225");
        assertThat(response.email()).isEqualTo("kim@example.com");
    }
}
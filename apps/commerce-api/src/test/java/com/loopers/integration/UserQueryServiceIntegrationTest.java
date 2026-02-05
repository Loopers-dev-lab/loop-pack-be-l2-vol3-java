package com.loopers.integration;

import com.loopers.application.service.UserQueryService;
import com.loopers.application.service.UserRegisterService;
import com.loopers.domain.model.*;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Import(MySqlTestContainersConfig.class)
class UserQueryServiceIntegrationTest {

    @Autowired
    private UserQueryService userQueryService;

    @Autowired
    private UserRegisterService userRegisterService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("가입된 사용자 정보 조회 성공")
    void getUserInfo_success() {
        // given
        UserId userId = UserId.of("query001");
        UserName userName = UserName.of("홍길동");
        Birthday birthday = Birthday.of(LocalDate.of(1990, 5, 15));
        Email email = Email.of("query@example.com");
        String encodedPassword = "encoded_password";

        userRegisterService.register(userId, userName, encodedPassword, birthday, email);

        // when
        var result = userQueryService.getUserInfo(userId);

        // then
        assertThat(result.loginId()).isEqualTo("query001");
        assertThat(result.maskedName()).isEqualTo("홍길*");
        assertThat(result.birthday()).isEqualTo("19900515");
        assertThat(result.email()).isEqualTo("query@example.com");
    }

    @Test
    @DisplayName("이름 마스킹 검증 - 2자 이름")
    void getUserInfo_maskedName_2chars() {
        // given
        UserId userId = UserId.of("mask0001");
        UserName userName = UserName.of("홍길");
        Birthday birthday = Birthday.of(LocalDate.of(1990, 5, 15));
        Email email = Email.of("mask@example.com");
        String encodedPassword = "encoded_password";

        userRegisterService.register(userId, userName, encodedPassword, birthday, email);

        // when
        var result = userQueryService.getUserInfo(userId);

        // then
        assertThat(result.maskedName()).isEqualTo("홍*");
    }

    @Test
    @DisplayName("이름 마스킹 검증 - 영문 이름")
    void getUserInfo_maskedName_english() {
        // given
        UserId userId = UserId.of("mask0002");
        UserName userName = UserName.of("John");
        Birthday birthday = Birthday.of(LocalDate.of(1990, 5, 15));
        Email email = Email.of("john@example.com");
        String encodedPassword = "encoded_password";

        userRegisterService.register(userId, userName, encodedPassword, birthday, email);

        // when
        var result = userQueryService.getUserInfo(userId);

        // then
        assertThat(result.maskedName()).isEqualTo("Joh*");
    }

    @Test
    @DisplayName("존재하지 않는 사용자 조회 시 예외")
    void getUserInfo_fail_not_found() {
        // given
        UserId userId = UserId.of("notexist");

        // when & then
        assertThatThrownBy(() -> userQueryService.getUserInfo(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }
}

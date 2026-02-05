package com.loopers.integration;

import com.loopers.application.service.PasswordUpdateService;
import com.loopers.application.service.UserRegisterService;
import com.loopers.domain.model.*;
import com.loopers.domain.repository.UserRepository;
import com.loopers.domain.service.PasswordEncoder;
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
class PasswordUpdateServiceIntegrationTest {

    @Autowired
    private PasswordUpdateService passwordUpdateService;

    @Autowired
    private UserRegisterService userRegisterService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final LocalDate BIRTHDAY = LocalDate.of(1990, 5, 15);

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("비밀번호 변경 성공")
    void updatePassword_success() {
        // given
        UserId userId = UserId.of("pwduser1");
        String rawPassword = "OldPass1!";
        String encodedPassword = passwordEncoder.encrypt(rawPassword);

        userRegisterService.register(
                userId,
                UserName.of("홍길동"),
                encodedPassword,
                Birthday.of(BIRTHDAY),
                Email.of("pwd@example.com")
        );

        Password currentPassword = Password.of(rawPassword, BIRTHDAY);
        Password newPassword = Password.of("NewPass1!", BIRTHDAY);

        // when
        passwordUpdateService.updatePassword(userId, currentPassword, newPassword);

        // then
        var updatedUser = userRepository.findById(userId).orElseThrow();
        assertThat(passwordEncoder.matches("NewPass1!", updatedUser.getEncodedPassword())).isTrue();
        assertThat(passwordEncoder.matches(rawPassword, updatedUser.getEncodedPassword())).isFalse();
    }

    @Test
    @DisplayName("현재 비밀번호 불일치 시 예외")
    void updatePassword_fail_wrong_current() {
        // given
        UserId userId = UserId.of("pwduser2");
        String rawPassword = "Correct1!";
        String encodedPassword = passwordEncoder.encrypt(rawPassword);

        userRegisterService.register(
                userId,
                UserName.of("홍길동"),
                encodedPassword,
                Birthday.of(BIRTHDAY),
                Email.of("pwd2@example.com")
        );

        Password wrongPassword = Password.of("WrongPw1!", BIRTHDAY);
        Password newPassword = Password.of("NewPass1!", BIRTHDAY);

        // when & then
        assertThatThrownBy(() -> passwordUpdateService.updatePassword(userId, wrongPassword, newPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 비밀번호가 일치하지 않습니다");
    }

    @Test
    @DisplayName("새 비밀번호가 현재와 동일하면 예외")
    void updatePassword_fail_same_password() {
        // given
        UserId userId = UserId.of("pwduser3");
        String rawPassword = "SamePass1!";
        String encodedPassword = passwordEncoder.encrypt(rawPassword);

        userRegisterService.register(
                userId,
                UserName.of("홍길동"),
                encodedPassword,
                Birthday.of(BIRTHDAY),
                Email.of("pwd3@example.com")
        );

        Password currentPassword = Password.of(rawPassword, BIRTHDAY);
        Password samePassword = Password.of(rawPassword, BIRTHDAY);

        // when & then
        assertThatThrownBy(() -> passwordUpdateService.updatePassword(userId, currentPassword, samePassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("현재 비밀번호는 사용할 수 없습니다");
    }

    @Test
    @DisplayName("존재하지 않는 사용자의 비밀번호 변경 시 예외")
    void updatePassword_fail_user_not_found() {
        // given
        UserId userId = UserId.of("notexist");
        Password currentPassword = Password.of("Current1!", BIRTHDAY);
        Password newPassword = Password.of("NewPass1!", BIRTHDAY);

        // when & then
        assertThatThrownBy(() -> passwordUpdateService.updatePassword(userId, currentPassword, newPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용자를 찾을 수 없습니다");
    }
}

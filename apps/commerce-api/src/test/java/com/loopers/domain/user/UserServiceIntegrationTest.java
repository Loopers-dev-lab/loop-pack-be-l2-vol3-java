package com.loopers.domain.user;

import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

@SpringBootTest
class UserServiceIntegrationTest {

    private static final String LOGIN_ID = "testuser1";
    private static final String RAW_PASSWORD = "Test1234!";
    private static final String NAME = "홍길동";
    private static final LocalDate BIRTH_DATE = LocalDate.of(1990, 1, 15);
    private static final String EMAIL = "test@example.com";

    @Autowired
    private UserService userService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입 시,")
    @Nested
    class Signup {

        @DisplayName("유효한 정보가 주어지면, DB에 저장되고 ID가 생성되며 비밀번호는 인코딩되어 저장된다.")
        @Test
        void createsUser_whenValidInfoIsProvided() {
            // act
            UserModel user = userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // assert
            assertAll(
                    () -> assertThat(user.getId()).isNotNull(),
                    () -> assertThat(user.getLoginId()).isEqualTo(LOGIN_ID),
                    () -> assertThat(user.getName()).isEqualTo(NAME),
                    () -> assertThat(user.getBirthDate()).isEqualTo(BIRTH_DATE),
                    () -> assertThat(user.getEmail()).isEqualTo(EMAIL),
                    () -> assertThat(passwordEncoder.matches(RAW_PASSWORD, user.getPassword())).isTrue()
            );
        }
    }

    @DisplayName("인증 시,")
    @Nested
    class Authenticate {

        @DisplayName("올바른 자격 증명이면, 사용자 정보를 반환한다.")
        @Test
        void returnsUser_whenCredentialsAreValid() {
            // arrange
            userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // act
            UserModel authenticated = userService.authenticate(LOGIN_ID, RAW_PASSWORD);

            // assert
            assertAll(
                    () -> assertThat(authenticated.getId()).isNotNull(),
                    () -> assertThat(authenticated.getLoginId()).isEqualTo(LOGIN_ID)
            );
        }

        @DisplayName("비밀번호가 틀리면, UNAUTHORIZED 예외가 발생한다.")
        @Test
        void throwsUnauthorized_whenPasswordMismatch() {
            // arrange
            userService.signup(LOGIN_ID, RAW_PASSWORD, NAME, BIRTH_DATE, EMAIL);

            // act
            CoreException exception = assertThrows(CoreException.class, () ->
                    userService.authenticate(LOGIN_ID, "WrongPass1!")
            );

            // assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.UNAUTHORIZED);
        }
    }
}
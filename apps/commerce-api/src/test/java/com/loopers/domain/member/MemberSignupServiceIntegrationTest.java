package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class MemberSignupServiceIntegrationTest {

    @Autowired
    private MemberSignupService memberSignupService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원가입")
    @Nested
    class Signup {

        @DisplayName("유효한 정보로 가입하면 회원이 생성되고 비밀번호가 암호화된다")
        @Test
        void createsMemberWithEncodedPassword() {
            // given
            String loginId = "kwonmo";
            String password = "Test1234!";
            String name = "양권모";
            LocalDate birthDate = LocalDate.of(1998, 9, 16);
            String email = "kwonmo@example.com";

            // when
            MemberModel result = memberSignupService.signup(loginId, password, name, birthDate, email);

            // then
            assertAll(
                () -> assertThat(result.getId()).isNotNull(),
                () -> assertThat(result.loginId().value()).isEqualTo(loginId),
                () -> assertThat(result.name().value()).isEqualTo(name),
                () -> assertThat(result.birthDate()).isEqualTo(birthDate),
                () -> assertThat(result.email().value()).isEqualTo(email),
                () -> assertThat(result.matchesPassword(password, passwordEncoder)).isTrue()
            );
        }

        @DisplayName("이미 존재하는 loginId로 가입하면 DUPLICATE_LOGIN_ID 예외가 발생한다")
        @Test
        void throwsOnDuplicateLoginId() {
            // given
            memberSignupService.signup("kwonmo", "Test1234!", "양권모",
                LocalDate.of(1998, 9, 16), "kwonmo@example.com");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                memberSignupService.signup("kwonmo", "Other1234!", "박지훈",
                    LocalDate.of(1995, 5, 20), "jihun@example.com"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.DUPLICATE_LOGIN_ID);
        }

        @DisplayName("loginId 형식이 잘못되면 INVALID_LOGIN_ID 예외가 발생한다")
        @Test
        void throwsOnInvalidLoginId() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                memberSignupService.signup("test@user", "Test1234!", "양권모",
                    LocalDate.of(1998, 9, 16), "kwonmo@example.com"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_LOGIN_ID);
        }

        @DisplayName("비밀번호 규칙을 위반하면 INVALID_PASSWORD 예외가 발생한다")
        @Test
        void throwsOnInvalidPassword() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                memberSignupService.signup("kwonmo", "short", "양권모",
                    LocalDate.of(1998, 9, 16), "kwonmo@example.com"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
        }

        @DisplayName("생년월일이 포함된 비밀번호는 거부한다")
        @Test
        void rejectsPasswordContainingBirthDate() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                memberSignupService.signup("kwonmo", "Pass19980916!", "양권모",
                    LocalDate.of(1998, 9, 16), "kwonmo@example.com"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }
}

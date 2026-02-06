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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
class MemberPasswordServiceIntegrationTest {

    @Autowired
    private MemberSignupService memberSignupService;

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private MemberPasswordService memberPasswordService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("비밀번호 변경")
    @Nested
    class ChangePassword {

        @DisplayName("올바른 현재 비밀번호와 유효한 새 비밀번호면 변경에 성공한다")
        @Test
        void changesPasswordSuccessfully() {
            // given
            MemberModel member = memberSignupService.signup("kwonmo", "Test1234!",
                "양권모", LocalDate.of(1998, 9, 16), "kwonmo@example.com");

            // when
            memberPasswordService.changePassword(member, "Test1234!", "NewPass5678!");

            // then
            MemberModel updated = memberAuthService.authenticate("kwonmo", "NewPass5678!");
            assertThat(updated.loginId().value()).isEqualTo("kwonmo");
        }

        @DisplayName("현재 비밀번호가 틀리면 PASSWORD_MISMATCH 예외가 발생한다")
        @Test
        void throwsOnWrongCurrentPassword() {
            // given
            MemberModel member = memberSignupService.signup("kwonmo", "Test1234!",
                "양권모", LocalDate.of(1998, 9, 16), "kwonmo@example.com");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                memberPasswordService.changePassword(member, "WrongPass1!", "NewPass5678!"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PASSWORD_MISMATCH);
        }

        @DisplayName("새 비밀번호가 현재와 같으면 PASSWORD_SAME_AS_OLD 예외가 발생한다")
        @Test
        void throwsWhenNewPasswordSameAsOld() {
            // given
            MemberModel member = memberSignupService.signup("kwonmo", "Test1234!",
                "양권모", LocalDate.of(1998, 9, 16), "kwonmo@example.com");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                memberPasswordService.changePassword(member, "Test1234!", "Test1234!"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PASSWORD_SAME_AS_OLD);
        }

        @DisplayName("새 비밀번호가 규칙에 맞지 않으면 INVALID_PASSWORD 예외가 발생한다")
        @Test
        void throwsOnInvalidNewPassword() {
            // given
            MemberModel member = memberSignupService.signup("kwonmo", "Test1234!",
                "양권모", LocalDate.of(1998, 9, 16), "kwonmo@example.com");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                memberPasswordService.changePassword(member, "Test1234!", "short"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.INVALID_PASSWORD);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면 거부한다")
        @Test
        void rejectsNewPasswordContainingBirthDate() {
            // given
            MemberModel member = memberSignupService.signup("kwonmo", "Test1234!",
                "양권모", LocalDate.of(1998, 9, 16), "kwonmo@example.com");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                memberPasswordService.changePassword(member, "Test1234!", "Pass19980916!"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.PASSWORD_CONTAINS_BIRTH_DATE);
        }
    }
}

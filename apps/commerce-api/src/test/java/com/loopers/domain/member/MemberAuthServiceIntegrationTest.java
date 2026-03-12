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
class MemberAuthServiceIntegrationTest {

    @Autowired
    private MemberSignupService memberSignupService;

    @Autowired
    private MemberAuthService memberAuthService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("회원 인증")
    @Nested
    class Authenticate {

        @DisplayName("올바른 loginId와 비밀번호면 회원을 반환한다")
        @Test
        void returnsMemberOnValidCredentials() {
            // given
            memberSignupService.signup("kwonmo", "Test1234!", "양권모",
                LocalDate.of(1998, 9, 16), "kwonmo@example.com");

            // when
            MemberModel result = memberAuthService.authenticate("kwonmo", "Test1234!");

            // then
            assertThat(result.loginId().value()).isEqualTo("kwonmo");
        }

        @DisplayName("비밀번호가 틀리면 인증 실패 예외가 발생한다")
        @Test
        void throwsOnWrongPassword() {
            // given
            memberSignupService.signup("kwonmo", "Test1234!", "양권모",
                LocalDate.of(1998, 9, 16), "kwonmo@example.com");

            // when
            CoreException result = assertThrows(CoreException.class, () ->
                memberAuthService.authenticate("kwonmo", "WrongPass1!"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.AUTHENTICATION_FAILED);
        }

        @DisplayName("존재하지 않는 loginId면 MEMBER_NOT_FOUND 예외가 발생한다")
        @Test
        void throwsOnNonExistentLoginId() {
            // given & when
            CoreException result = assertThrows(CoreException.class, () ->
                memberAuthService.authenticate("nobody", "Test1234!"));

            // then
            assertThat(result.getErrorType()).isEqualTo(ErrorType.MEMBER_NOT_FOUND);
        }
    }
}

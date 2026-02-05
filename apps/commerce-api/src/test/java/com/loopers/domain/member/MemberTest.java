package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberTest {

    private static final String VALID_LOGIN_ID = "testuser1";
    private static final String VALID_PASSWORD = "Test1234!";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTHDAY = LocalDate.of(1995, 3, 15);
    private static final String VALID_EMAIL = "test@example.com";

    @DisplayName("회원을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("모든 값이 유효하면, 정상적으로 생성된다.")
        @Test
        void createsMember_whenAllFieldsAreValid() {
            // arrange & act
            Member member = new Member(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // assert
            assertAll(
                () -> assertThat(member.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(member.getPassword()).isEqualTo(VALID_PASSWORD),
                () -> assertThat(member.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(member.getBirthday()).isEqualTo(VALID_BIRTHDAY),
                () -> assertThat(member.getEmail()).isEqualTo(VALID_EMAIL)
            );
        }
    }

    @DisplayName("비밀번호를 검증할 때,")
    @Nested
    class ValidatePassword {

        @DisplayName("생년월일(yyyyMMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthday() {
            // arrange
            LocalDate birthday = LocalDate.of(1995, 3, 15);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Member(VALID_LOGIN_ID, "A19950315!", VALID_NAME, birthday, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("생년월일을 검증할 때,")
    @Nested
    class ValidateBirthday {

        @DisplayName("오늘 날짜(경계값)이면, 정상적으로 생성된다.")
        @Test
        void createsSuccessfully_whenBirthdayIsToday() {
            // arrange
            LocalDate today = LocalDate.now();

            // act
            Member member = new Member(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, today, VALID_EMAIL);

            // assert
            assertThat(member.getBirthday()).isEqualTo(today);
        }

        @DisplayName("미래 날짜이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthdayIsFuture() {
            // arrange
            LocalDate futureDate = LocalDate.now().plusDays(1);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Member(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, futureDate, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 암호화할 때,")
    @Nested
    class EncryptPassword {

        @DisplayName("암호화된 비밀번호로 교체된다.")
        @Test
        void replacesPasswordWithEncoded() {
            // arrange
            Member member = new Member(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);
            String encodedPassword = "$2a$10$encodedPasswordHash";

            // act
            member.encryptPassword(encodedPassword);

            // assert
            assertThat(member.getPassword()).isEqualTo(encodedPassword);
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 새 비밀번호로 변경하면, 비밀번호가 변경된다.")
        @Test
        void changesPassword_whenNewPasswordIsValid() {
            // arrange
            Member member = new Member(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);
            String newEncodedPassword = "$2a$10$newEncodedPasswordHash";

            // act
            member.changePassword("NewPass123!", newEncodedPassword);

            // assert
            assertThat(member.getPassword()).isEqualTo(newEncodedPassword);
        }

        @DisplayName("새 비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsBirthday() {
            // arrange
            Member member = new Member(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                member.changePassword("A19950315!", "encoded")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}
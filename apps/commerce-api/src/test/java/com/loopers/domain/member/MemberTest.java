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

    @DisplayName("로그인 ID를 검증할 때,")
    @Nested
    class ValidateLoginId {

        @DisplayName("null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Member(null, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Member("   ", VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 검증할 때,")
    @Nested
    class ValidatePassword {

        @DisplayName("8자(MIN)이면, 정상적으로 생성된다.")
        @Test
        void createsSuccessfully_whenPasswordIsMinLength() {
            // act
            Member member = new Member(VALID_LOGIN_ID, "Test123!", VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // assert
            assertThat(member.getPassword()).isEqualTo("Test123!");
        }

        @DisplayName("16자(MAX)이면, 정상적으로 생성된다.")
        @Test
        void createsSuccessfully_whenPasswordIsMaxLength() {
            // act
            Member member = new Member(VALID_LOGIN_ID, "Test12345678901!", VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // assert
            assertThat(member.getPassword()).isEqualTo("Test12345678901!");
        }

        @DisplayName("8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Member(VALID_LOGIN_ID, "Test12!", VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Member(VALID_LOGIN_ID, "Test1234!Test1234", VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("허용되지 않는 문자(한글 등)가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsInvalidCharacters() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Member(VALID_LOGIN_ID, "Test한글1234!", VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

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

    @DisplayName("이름을 검증할 때,")
    @Nested
    class ValidateName {

        @DisplayName("한글 2자(MIN)이면, 정상적으로 생성된다.")
        @Test
        void createsSuccessfully_whenNameIsMinLength() {
            // act
            Member member = new Member(VALID_LOGIN_ID, VALID_PASSWORD, "홍길", VALID_BIRTHDAY, VALID_EMAIL);

            // assert
            assertThat(member.getName()).isEqualTo("홍길");
        }

        @DisplayName("한글 20자(MAX)이면, 정상적으로 생성된다.")
        @Test
        void createsSuccessfully_whenNameIsMaxLength() {
            // arrange
            String maxName = "가나다라마바사아자차카타파하가나다라마바";

            // act
            Member member = new Member(VALID_LOGIN_ID, VALID_PASSWORD, maxName, VALID_BIRTHDAY, VALID_EMAIL);

            // assert
            assertThat(member.getName()).isEqualTo(maxName);
        }

        @DisplayName("한글 1자이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsTooShort() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Member(VALID_LOGIN_ID, VALID_PASSWORD, "홍", VALID_BIRTHDAY, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("한글 20자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsTooLong() {
            // arrange
            String longName = "가나다라마바사아자차카타파하가나다라마바사";

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Member(VALID_LOGIN_ID, VALID_PASSWORD, longName, VALID_BIRTHDAY, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("한글이 아닌 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameContainsNonKorean() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Member(VALID_LOGIN_ID, VALID_PASSWORD, "홍gildong", VALID_BIRTHDAY, VALID_EMAIL)
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

        @DisplayName("null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthdayIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Member(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, null, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
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

    @DisplayName("이메일을 검증할 때,")
    @Nested
    class ValidateEmail {

        @DisplayName("null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Member(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, null)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new Member(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, "invalid-email")
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

        @DisplayName("새 비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsTooShort() {
            // arrange
            Member member = new Member(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                member.changePassword("New12!", "encoded")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordIsTooLong() {
            // arrange
            Member member = new Member(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                member.changePassword("NewPass12345678!!", "encoded")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
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

        @DisplayName("새 비밀번호에 허용되지 않는 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNewPasswordContainsInvalidCharacters() {
            // arrange
            Member member = new Member(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                member.changePassword("New한글1234!", "encoded")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

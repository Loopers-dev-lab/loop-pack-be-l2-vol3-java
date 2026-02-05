package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

    private static final String VALID_LOGIN_ID = "testuser123";
    private static final String VALID_ENCODED_PASSWORD = "$2a$10$encodedPassword";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1990, 5, 15);
    private static final String VALID_EMAIL = "test@example.com";

    @DisplayName("User 생성 시,")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenAllFieldsAreValid() {
            // arrange & act
            User user = new User(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // assert
            assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(user.getPassword()).isEqualTo(VALID_ENCODED_PASSWORD),
                () -> assertThat(user.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(user.getBirthDate()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(user.getEmail()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("로그인 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsNull() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                new User(null, VALID_ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsSpecialCharacters() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                new User("test@user!", VALID_ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                new User(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, null, VALID_BIRTH_DATE, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 미래면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsFuture() {
            // arrange
            LocalDate futureBirthDate = LocalDate.now().plusDays(1);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                new User(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, VALID_NAME, futureBirthDate, VALID_EMAIL)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                new User(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "invalid-email")
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호 검증 시,")
    @Nested
    class ValidateRawPassword {

        @DisplayName("유효한 비밀번호면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenPasswordIsValid() {
            // arrange & act & assert
            assertDoesNotThrow(() ->
                User.validateRawPassword("Password1!", VALID_BIRTH_DATE)
            );
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooShort() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                User.validateRawPassword("Pass1!", VALID_BIRTH_DATE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자 초과면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordIsTooLong() {
            // arrange
            String longPassword = "Password1!" + "a".repeat(7);

            // act
            CoreException result = assertThrows(CoreException.class, () ->
                User.validateRawPassword(longPassword, VALID_BIRTH_DATE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsKorean() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                User.validateRawPassword("Pass한글1!", VALID_BIRTH_DATE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate_yyyyMMdd() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                User.validateRawPassword("Pass19900515!", VALID_BIRTH_DATE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(yyMMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthDate_yyMMdd() {
            // arrange & act
            CoreException result = assertThrows(CoreException.class, () ->
                User.validateRawPassword("Pass900515!!", VALID_BIRTH_DATE)
            );

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("이름 마스킹 시,")
    @Nested
    class GetMaskedName {

        @DisplayName("이름이 2자 이상이면, 마지막 글자가 *로 마스킹된다.")
        @Test
        void masksLastCharacter_whenNameHasMultipleCharacters() {
            // arrange
            User user = new User(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, "홍길동", VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            String maskedName = user.getMaskedName();

            // assert
            assertThat(maskedName).isEqualTo("홍길*");
        }

        @DisplayName("이름이 1자이면, *로 반환된다.")
        @Test
        void returnsStar_whenNameHasSingleCharacter() {
            // arrange
            User user = new User(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, "홍", VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            String maskedName = user.getMaskedName();

            // assert
            assertThat(maskedName).isEqualTo("*");
        }
    }

    @DisplayName("비밀번호 변경 시,")
    @Nested
    class ChangePassword {

        @DisplayName("새로운 인코딩된 비밀번호로 변경된다.")
        @Test
        void changesPassword_whenNewEncodedPasswordIsProvided() {
            // arrange
            User user = new User(VALID_LOGIN_ID, VALID_ENCODED_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            String newEncodedPassword = "$2a$10$newEncodedPassword";

            // act
            user.changePassword(newEncodedPassword);

            // assert
            assertThat(user.getPassword()).isEqualTo(newEncodedPassword);
        }
    }
}

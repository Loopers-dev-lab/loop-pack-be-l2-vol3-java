package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

    private static final String VALID_LOGIN_ID = "testUser1";
    private static final String VALID_PASSWORD = "encrypted_password";
    private static final String VALID_NAME = "홍길동";
    private static final LocalDate VALID_BIRTH_DATE = LocalDate.of(1995, 3, 15);
    private static final String VALID_EMAIL = "test@example.com";

    @DisplayName("유저를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 정보가 올바르면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenAllFieldsAreValid() {
            // act
            User user = new User(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // assert
            assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo(VALID_LOGIN_ID),
                () -> assertThat(user.getPassword()).isEqualTo(VALID_PASSWORD),
                () -> assertThat(user.getName()).isEqualTo(VALID_NAME),
                () -> assertThat(user.getBirthDate()).isEqualTo(VALID_BIRTH_DATE),
                () -> assertThat(user.getEmail()).isEqualTo(VALID_EMAIL)
            );
        }

        @DisplayName("로그인 ID가 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User(null, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID가 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("  ", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID에 영문/숫자 외 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsSpecialChars() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("user@123", VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User(VALID_LOGIN_ID, VALID_PASSWORD, null, VALID_BIRTH_DATE, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User(VALID_LOGIN_ID, VALID_PASSWORD, "  ", VALID_BIRTH_DATE, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "  ");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, "invalid-email");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthDateIsNull() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, null, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("새 비밀번호로 변경된다.")
        @Test
        void changesPassword_whenNewPasswordProvided() {
            // arrange
            User user = new User(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            user.changePassword("new_encrypted_password");

            // assert
            assertThat(user.getPassword()).isEqualTo("new_encrypted_password");
        }
    }

    @DisplayName("이름을 마스킹할 때, ")
    @Nested
    class GetMaskedName {

        @DisplayName("마지막 글자가 '*'로 대체된다.")
        @Test
        void masksLastCharacter() {
            // arrange
            User user = new User(VALID_LOGIN_ID, VALID_PASSWORD, "홍길동", VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            String result = user.getMaskedName();

            // assert
            assertThat(result).isEqualTo("홍길*");
        }

        @DisplayName("이름이 한 글자이면, '*'로 대체된다.")
        @Test
        void masksSingleCharacterName() {
            // arrange
            User user = new User(VALID_LOGIN_ID, VALID_PASSWORD, "홍", VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            String result = user.getMaskedName();

            // assert
            assertThat(result).isEqualTo("*");
        }

        @DisplayName("이름이 두 글자이면, 마지막 글자만 '*'로 대체된다.")
        @Test
        void masksTwoCharacterName() {
            // arrange
            User user = new User(VALID_LOGIN_ID, VALID_PASSWORD, "홍길", VALID_BIRTH_DATE, VALID_EMAIL);

            // act
            String result = user.getMaskedName();

            // assert
            assertThat(result).isEqualTo("홍*");
        }
    }

}

package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    private static final String VALID_LOGIN_ID = "namjin123";
    private static final String VALID_PASSWORD = "qwer@1234";
    private static final String VALID_NAME = "namjin";
    private static final String VALID_BIRTHDAY = "1994-05-25";
    private static final String VALID_EMAIL = "epemxksl@gmail.com";

    @DisplayName("회원을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 정보가 올바르면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenAllInfoIsProvided() {
            // act
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // assert
            assertThat(user).isNotNull();
        }

        @DisplayName("로그인 ID가 비어있으면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("", VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID에 영문과 숫자 외 문자가 포함되면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdContainsInvalidCharacters() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("namjin123!@#", VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 비어있으면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, "", VALID_BIRTHDAY, VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 포맷이 올바르지 않으면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenEmailFormatIsInvalid() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, "epemxksl@gmail-com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 포맷이 올바르지 않으면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBirthDateFormatIsInvalid() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, "1994-005-25", VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 현재보다 이후의 날짜면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBirthDateisAfterToday(){
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, "2027-01-01", VALID_EMAIL);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 변경할 때, ")
    @Nested
    class ChangePassword {

        @DisplayName("정상적인 암호화된 비밀번호로 변경하면, 비밀번호가 업데이트된다.")
        @Test
        void changesPassword_whenEncryptedPasswordIsValid() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);
            String newEncryptedPassword = "new_encrypted_password";

            // act
            user.changePassword(newEncryptedPassword);

            // assert
            assertThat(user.getPassword()).isEqualTo(newEncryptedPassword);
        }

        @DisplayName("blank 비밀번호로 변경하면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordIsBlank() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                user.changePassword("");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("null 비밀번호로 변경하면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordIsNull() {
            // arrange
            UserModel user = new UserModel(VALID_LOGIN_ID, VALID_PASSWORD, VALID_NAME, VALID_BIRTHDAY, VALID_EMAIL);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                user.changePassword(null);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

package com.loopers.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

class UserTest {

    @DisplayName("User를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("로그인 ID가 영문과 숫자로만 이루어져 있으면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenLoginIdIsAlphanumeric() {
            // arrange
            String loginId = "user123";

            // act & assert
            assertThatCode(() -> new User(loginId, "Password1!", "홍길동", "1990-01-01", "test@email.com"))
                    .doesNotThrowAnyException();
        }

        @DisplayName("로그인 ID에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdContainsSpecialCharacter() {
            // arrange
            String loginId = "user@123";

            // act & assert
            assertThatThrownBy(() -> new User(loginId, "Password1!", "홍길동", "1990-01-01", "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("로그인 ID에 한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdContainsKorean() {
            // arrange
            String loginId = "user한글";

            // act & assert
            assertThatThrownBy(() -> new User(loginId, "Password1!", "홍길동", "1990-01-01", "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("로그인 ID에 공백이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdContainsSpace() {
            // arrange
            String loginId = "user 123";

            // act & assert
            assertThatThrownBy(() -> new User(loginId, "Password1!", "홍길동", "1990-01-01", "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("이메일 형식이 올바르면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenEmailFormatIsValid() {
            // arrange
            String email = "user@domain.com";

            // act & assert
            assertThatCode(() -> new User("user123", "Password1!", "홍길동", "1990-01-01", email))
                    .doesNotThrowAnyException();
        }

        @DisplayName("이메일에 @가 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenEmailMissingAtSign() {
            // arrange
            String email = "userdomain.com";

            // act & assert
            assertThatThrownBy(() -> new User("user123", "Password1!", "홍길동", "1990-01-01", email))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("이메일 도메인에 점이 없으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenEmailMissingDotInDomain() {
            // arrange
            String email = "user@domaincom";

            // act & assert
            assertThatThrownBy(() -> new User("user123", "Password1!", "홍길동", "1990-01-01", email))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("생년월일이 yyyy-MM-dd 형식이면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenBirthDateFormatIsValid() {
            // arrange
            String birthDate = "1990-01-15";

            // act & assert
            assertThatCode(() -> new User("user123", "Password1!", "홍길동", birthDate, "test@email.com"))
                    .doesNotThrowAnyException();
        }

        @DisplayName("생년월일이 yyyy-MM-dd 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBirthDateFormatIsInvalid() {
            // arrange
            String birthDate = "19900115";

            // act & assert
            assertThatThrownBy(() -> new User("user123", "Password1!", "홍길동", birthDate, "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("생년월일의 월이 12를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBirthDateMonthExceeds12() {
            // arrange
            String birthDate = "1990-13-01";

            // act & assert
            assertThatThrownBy(() -> new User("user123", "Password1!", "홍길동", birthDate, "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("생년월일의 일이 31을 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBirthDateDayExceeds31() {
            // arrange
            String birthDate = "1990-01-32";

            // act & assert
            assertThatThrownBy(() -> new User("user123", "Password1!", "홍길동", birthDate, "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("이름이 빈 값이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsEmpty() {
            // arrange
            String name = "";

            // act & assert
            assertThatThrownBy(() -> new User("user123", "Password1!", name, "1990-01-01", "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("이름이 공백만 있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            String name = "   ";

            // act & assert
            assertThatThrownBy(() -> new User("user123", "Password1!", name, "1990-01-01", "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("비밀번호가 8자 이상 16자 이하이면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenPasswordLengthIsValid() {
            // arrange
            String password = "Passw1!a";

            // act & assert
            assertThatCode(() -> new User("user123", password, "홍길동", "1990-01-01", "test@email.com"))
                    .doesNotThrowAnyException();
        }

        @DisplayName("비밀번호가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordTooShort() {
            // arrange
            String password = "Pass1!a";

            // act & assert
            assertThatThrownBy(() -> new User("user123", password, "홍길동", "1990-01-01", "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("비밀번호가 16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordTooLong() {
            // arrange
            String password = "Password1!abcdefg";

            // act & assert
            assertThatThrownBy(() -> new User("user123", password, "홍길동", "1990-01-01", "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("비밀번호에 한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsKorean() {
            // arrange
            String password = "Pass한글1!";

            // act & assert
            assertThatThrownBy(() -> new User("user123", password, "홍길동", "1990-01-01", "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("비밀번호에 공백이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsSpace() {
            // arrange
            String password = "Pass 1!ab";

            // act & assert
            assertThatThrownBy(() -> new User("user123", password, "홍길동", "1990-01-01", "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsBirthDate() {
            // arrange
            String password = "Pass19900101!";
            String birthDate = "1990-01-01";

            // act & assert
            assertThatThrownBy(() -> new User("user123", password, "홍길동", birthDate, "test@email.com"))
                    .isInstanceOf(CoreException.class)
                    .satisfies(e -> assertThat(((CoreException) e).getErrorType()).isEqualTo(ErrorType.BAD_REQUEST));
        }
    }
}

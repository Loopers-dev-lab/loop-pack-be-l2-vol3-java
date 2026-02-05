package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    @DisplayName("유저를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("로그인ID, 비밀번호, 이름, 생년월일, 이메일이 모두 주어지면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenAllRequiredFieldsAreProvided() {
            // arrange
            String loginId = "testuser";
            String password = "Test1234!";
            String name = "홍길동";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);
            String email = "test@example.com";

            // act
            UserModel user = new UserModel(loginId, password, name, birthDate, email);

            // assert
            assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo(loginId),
                () -> assertThat(user.getPassword().getValue()).isEqualTo(password),
                () -> assertThat(user.getName()).isEqualTo(name),
                () -> assertThat(user.getBirthDate()).isEqualTo(birthDate),
                () -> assertThat(user.getEmail().getValue()).isEqualTo(email)
            );
        }

        @DisplayName("로그인ID가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdIsBlank() {
            // arrange
            String loginId = "   ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(loginId, "Test1234!", "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordIsBlank() {
            // arrange
            String password = "";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("testuser", password, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            String name = "   ";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("testuser", "Test1234!", name, LocalDate.of(1990, 1, 15), "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 null이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBirthDateIsNull() {
            // arrange
            LocalDate birthDate = null;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("testuser", "Test1234!", "홍길동", birthDate, "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일이 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenEmailIsBlank() {
            // arrange
            String email = "";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("testuser", "Test1234!", "홍길동", LocalDate.of(1990, 1, 15), email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 8자 미만이거나 16자 초과면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"Test12!", "Test1234!Test1234"})
        void throwsBadRequestException_whenPasswordLengthIsInvalid(String password) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("testuser", password, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 영문 대소문자, 숫자, 특수문자 외의 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsInvalidCharacters() {
            // arrange
            String password = "Test1234한글";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("testuser", password, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsBirthDate() {
            // arrange
            String password = "Test19900115!";
            LocalDate birthDate = LocalDate.of(1990, 1, 15);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("testuser", password, "홍길동", birthDate, "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일은 이메일 형식이어야 하고 이메일 형식이 아니면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenEmailFormatIsInvalid(){

            // arrange
            String email = "test123.com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("testuser", "Test1234!", "홍길동", LocalDate.of(1990, 1, 15), email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }


        @DisplayName("로그인 ID는 영문과 숫자형식이고 대문자 소문자 구분은 안한다.")
        @Test
        void throwsException_whenLoginIdFormatIsInvalid(){

            // arrange
            String loginId = "test12아 아";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(loginId, "Test1234!", "홍길동", LocalDate.of(1990, 1, 15), "test123@abc.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름은 한글만 가능합니다..")
        @Test
        void throwsException_whenNameIsInvalid(){

            // arrange
            String name = "te사과";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("Test1221", "Test1234!", name, LocalDate.of(1990, 1, 15), "test123@abc.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 미래 날짜면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBirthDateIsFuture() {
            // arrange
            LocalDate futureBirthDate = LocalDate.now().plusDays(1);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("testuser", "Test1234!", "홍길동", futureBirthDate, "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일이 150년 이전이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBirthDateIsTooOld() {
            // arrange
            LocalDate tooOldBirthDate = LocalDate.now().minusYears(151);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel("testuser", "Test1234!", "홍길동", tooOldBirthDate, "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

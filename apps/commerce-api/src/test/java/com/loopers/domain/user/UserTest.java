package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

    @DisplayName("User를 생성할 때,")
    @Nested
    class Create {

        @DisplayName("유효한 입력이 주어지면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenValidInputIsProvided() {
            // arrange
            String loginId = "testuser123";
            String password = "Test1234!";
            String name = "홍길동";
            String birthDate = "19900101";
            String email = "test@example.com";

            // act
            User user = new User(loginId, password, name, birthDate, email);

            // assert
            assertAll(
                () -> assertThat(user.getLoginId()).isEqualTo(loginId),
                () -> assertThat(user.getName()).isEqualTo(name),
                () -> assertThat(user.getBirthDate()).isEqualTo(birthDate),
                () -> assertThat(user.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("loginId가 null이거나 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        void throwsBadRequestException_whenLoginIdIsNullOrEmpty(String loginId) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User(loginId, "Test1234!", "홍길동", "19900101", "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("loginId가 영문+숫자 외 문자를 포함하면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"test@user", "test user", "테스트유저", "test_user"})
        void throwsBadRequestException_whenLoginIdContainsInvalidChars(String loginId) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User(loginId, "Test1234!", "홍길동", "19900101", "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("loginId가 30바이트를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdExceeds30Bytes() {
            // arrange
            String loginId = "a".repeat(31);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User(loginId, "Test1234!", "홍길동", "19900101", "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 null이거나 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        void throwsBadRequestException_whenNameIsNullOrEmpty(String name) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("testuser", "Test1234!", name, "19900101", "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 한글+영문 외 문자를 포함하면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"홍길동123", "홍길동!", "홍 길동"})
        void throwsBadRequestException_whenNameContainsInvalidChars(String name) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("testuser", "Test1234!", name, "19900101", "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 30바이트를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameExceeds30Bytes() {
            // arrange - 한글 1자 = 3바이트, 11자 = 33바이트
            String name = "가".repeat(11);

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("testuser", "Test1234!", name, "19900101", "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("birthDate가 null이거나 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        void throwsBadRequestException_whenBirthDateIsNullOrEmpty(String birthDate) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("testuser", "Test1234!", "홍길동", birthDate, "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("birthDate가 YYYYMMDD 포맷이 아니면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"1990-01-01", "19901", "990101", "2000/01/01"})
        void throwsBadRequestException_whenBirthDateHasInvalidFormat(String birthDate) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("testuser", "Test1234!", "홍길동", birthDate, "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("birthDate가 유효하지 않은 날짜면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"19901301", "19900132", "20000230", "19000001"})
        void throwsBadRequestException_whenBirthDateIsInvalidDate(String birthDate) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("testuser", "Test1234!", "홍길동", birthDate, "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("email이 null이거나 빈 문자열이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @NullAndEmptySource
        void throwsBadRequestException_whenEmailIsNullOrEmpty(String email) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("testuser", "Test1234!", "홍길동", "19900101", email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("email이 유효하지 않은 형식이면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"test", "test@", "@example.com", "test@.com", "test@com"})
        void throwsBadRequestException_whenEmailHasInvalidFormat(String email) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("testuser", "Test1234!", "홍길동", "19900101", email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password가 8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordIsTooShort() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("testuser", "Test12!", "홍길동", "19900101", "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password가 16자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordIsTooLong() {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("testuser", "Test1234567890123!", "홍길동", "19900101", "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password에 허용되지 않은 문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"Test1234~", "Test1234()", "Test1234<>"})
        void throwsBadRequestException_whenPasswordContainsInvalidChars(String password) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("testuser", password, "홍길동", "19900101", "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("password에 생년월일 4자리 이상 부분문자열이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @ParameterizedTest
        @ValueSource(strings = {"Test1990!", "Test0101!", "Test9001!"})
        void throwsBadRequestException_whenPasswordContainsBirthDateSubstring(String password) {
            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new User("testuser", password, "홍길동", "19900101", "test@example.com");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 검증할 때,")
    @Nested
    class MatchPassword {

        @DisplayName("비밀번호가 일치하면, true를 반환한다.")
        @Test
        void returnsTrue_whenPasswordMatches() {
            // arrange
            User user = new User("testuser", "Test1234!", "홍길동", "19900101", "test@example.com");

            // act
            boolean result = user.matchPassword("Test1234!");

            // assert
            assertThat(result).isTrue();
        }

        @DisplayName("비밀번호가 일치하지 않으면, false를 반환한다.")
        @Test
        void returnsFalse_whenPasswordDoesNotMatch() {
            // arrange
            User user = new User("testuser", "Test1234!", "홍길동", "19900101", "test@example.com");

            // act
            boolean result = user.matchPassword("WrongPass1!");

            // assert
            assertThat(result).isFalse();
        }
    }

    @DisplayName("비밀번호를 변경할 때,")
    @Nested
    class ChangePassword {

        @DisplayName("유효한 새 비밀번호로 변경하면, 성공한다.")
        @Test
        void succeeds_whenNewPasswordIsValid() {
            // arrange
            User user = new User("testuser", "Test1234!", "홍길동", "19900101", "test@example.com");

            // act
            user.changePassword("NewPass12!");

            // assert
            assertThat(user.matchPassword("NewPass12!")).isTrue();
        }

        @DisplayName("현재 비밀번호와 동일하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewPasswordIsSameAsCurrent() {
            // arrange
            User user = new User("testuser", "Test1234!", "홍길동", "19900101", "test@example.com");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                user.changePassword("Test1234!");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("새 비밀번호가 규칙을 위반하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNewPasswordViolatesRules() {
            // arrange
            User user = new User("testuser", "Test1234!", "홍길동", "19900101", "test@example.com");

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                user.changePassword("short");
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("이름을 마스킹할 때,")
    @Nested
    class GetMaskedName {

        @DisplayName("2글자 이상이면, 첫 글자와 마지막 글자만 보이고 중간은 *로 마스킹된다.")
        @Test
        void masksMiddleCharacters_whenNameHasTwoOrMoreCharacters() {
            // arrange
            User user = new User("testuser", "Test1234!", "홍길동", "19900101", "test@example.com");

            // act
            String result = user.getMaskedName();

            // assert
            assertThat(result).isEqualTo("홍*동");
        }

        @DisplayName("1글자이면, 그대로 반환된다.")
        @Test
        void returnsAsIs_whenNameHasOneCharacter() {
            // arrange
            User user = new User("testuser", "Test1234!", "김", "19900101", "test@example.com");

            // act
            String result = user.getMaskedName();

            // assert
            assertThat(result).isEqualTo("김");
        }

        @DisplayName("영문 이름도 마스킹된다.")
        @Test
        void masksEnglishName() {
            // arrange
            User user = new User("testuser", "Test1234!", "John", "19900101", "test@example.com");

            // act
            String result = user.getMaskedName();

            // assert
            assertThat(result).isEqualTo("J**n");
        }
    }
}

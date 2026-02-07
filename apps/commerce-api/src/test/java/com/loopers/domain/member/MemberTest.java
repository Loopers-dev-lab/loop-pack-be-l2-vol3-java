package com.loopers.domain.member;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemberTest {

    @DisplayName("회원을 생성할 때,")
    @Nested
    class Create {

        @DisplayName("모든 필드가 유효하면, 정상적으로 생성된다.")
        @Test
        void createsMember_whenAllFieldsAreValid() {
            // Arrange
            String loginId = "testuser";
            String encryptedPassword = "$2a$10$dummyEncryptedPassword";
            String name = "홍길동";
            String birthday = "19900101";
            String email = "test@example.com";

            // Act
            Member member = new Member(loginId, encryptedPassword, name, birthday, email);

            // Assert
            assertAll(
                () -> assertThat(member.getLoginId()).isEqualTo(loginId),
                () -> assertThat(member.getPassword()).isEqualTo(encryptedPassword),
                () -> assertThat(member.getName()).isEqualTo(name),
                () -> assertThat(member.getBirthday()).isEqualTo(birthday),
                () -> assertThat(member.getEmail()).isEqualTo(email)
            );
        }

        @DisplayName("로그인 ID에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsSpecialChars() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                new Member("test!!!", "encrypted", "홍길동", "19900101", "test@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID가 10자를 초과하면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdExceedsTenChars() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                new Member("abcdefghijk", "encrypted", "홍길동", "19900101", "test@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID에 한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdContainsKorean() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                new Member("테스트user", "encrypted", "홍길동", "19900101", "test@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("로그인 ID가 비어있으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenLoginIdIsBlank() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                new Member("", "encrypted", "홍길동", "19900101", "test@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름에 숫자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameContainsNumbers() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                new Member("testuser", "encrypted", "홍길동123", "19900101", "test@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름에 특수문자가 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenNameContainsSpecialChars() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                new Member("testuser", "encrypted", "홍길동!@", "19900101", "test@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 형식이 올바르지 않으면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenEmailFormatIsInvalid() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                new Member("testuser", "encrypted", "홍길동", "19900101", "invalid-email")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생일이 yyyyMMdd 형식이 아니면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthdayFormatIsInvalid() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                new Member("testuser", "encrypted", "홍길동", "1990-01-01", "test@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생일이 존재하지 않는 날짜이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenBirthdayIsNonexistentDate() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                new Member("testuser", "encrypted", "홍길동", "20230230", "test@example.com")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }

    @DisplayName("비밀번호를 검증")
    @Nested
    class ValidatePassword {

        @DisplayName("8자 미만이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordTooShort() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                Member.validateRawPassword("Short1!", "19900101")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("16자 초과이면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordTooLong() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                Member.validateRawPassword("ThisIsWayTooLong1!", "19900101")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("한글이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsKorean() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                Member.validateRawPassword("Test한글1234!", "19900101")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("공백이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsSpace() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                Member.validateRawPassword("Test 1234!", "19900101")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생일이 포함되면, BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequest_whenPasswordContainsBirthday() {
            // Arrange & Act
            CoreException exception = assertThrows(CoreException.class, () ->
                Member.validateRawPassword("abc19900101!", "19900101")
            );

            // Assert
            assertThat(exception.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("유효한 비밀번호이면, 예외가 발생하지 않는다.")
        @Test
        void doesNotThrow_whenPasswordIsValid() {
            // Arrange & Act & Assert
            Member.validateRawPassword("Test1234!", "19900101");
        }
    }

    @DisplayName("이름 마스킹")
    @Nested
    class GetMaskedName {

        @DisplayName("이름의 마지막 글자가 *으로 반환된다.")
        @Test
        void returnsNameWithLastCharMasked() {
            // Arrange
            Member member = new Member("testuser", "encrypted", "홍길동", "19900101", "test@example.com");

            // Act
            String maskedName = member.getMaskedName();

            // Assert
            assertThat(maskedName).isEqualTo("홍길*");
        }

        @DisplayName("한 글자 이름이면 *으로 반환된다.")
        @Test
        void returnsStar_whenNameIsSingleChar() {
            // Arrange
            Member member = new Member("testuser", "encrypted", "김", "19900101", "test@example.com");

            // Act
            String maskedName = member.getMaskedName();

            // Assert
            assertThat(maskedName).isEqualTo("*");
        }
    }

    @DisplayName("비밀번호 변경")
    @Nested
    class ChangePassword {

        @DisplayName("새 비밀번호로 변경하면, 비밀번호가 업데이트된다.")
        @Test
        void updatesPassword_whenNewPasswordIsProvided() {
            // Arrange
            Member member = new Member("testuser", "oldEncrypted", "홍길동", "19900101", "test@example.com");
            String newEncryptedPassword = "newEncrypted";

            // Act
            member.changePassword(newEncryptedPassword);

            // Assert
            assertThat(member.getPassword()).isEqualTo(newEncryptedPassword);
        }
    }
}

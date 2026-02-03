package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserModelTest {

    @DisplayName("회원을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 정보가 올바르면, 정상적으로 생성된다.")
        @Test
        void createsUser_whenAllInfoIsProvided() {
            // arrange
            String loginId = "namjin123";
            String password = "qwer@1234";
            String name = "namjin";
            String birthDay = "1994-05-25";
            String email = "epemxksl@gmail.com";

            // act
            UserModel user = new UserModel(loginId, password, name, birthDay, email);

            // assert
            assertThat(user).isNotNull();
        }

        @DisplayName("로그인 ID가 비어있으면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdIsBlank() {
            // arrange
            String loginId = "";
            String password = "qwer@1234";
            String name = "namjin";
            String birthDay = "1994-05-25";
            String email = "epemxksl@gmail.com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(loginId, password, name, birthDay, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 비어있으면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsBlank() {
            // arrange
            String loginId = "namjin123";
            String password = "qwer@1234";
            String name = "";
            String birthDay = "1994-05-25";
            String email = "epemxksl@gmail.com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(loginId, password, name, birthDay, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이메일 포맷이 올바르지 않으면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenEmailFormatIsInvalid() {
            // arrange
            String loginId = "namjin123";
            String password = "qwer@1234";
            String name = "namjin";
            String birthDay = "1994-05-25";
            String email = "epemxksl@gmail-com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(loginId, password, name, birthDay, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("생년월일 포맷이 올바르지 않으면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBirthDateFormatIsInvalid() {
            // arrange
            String loginId = "namjin123";
            String password = "qwer@1234";
            String name = "namjin";
            String birthDay = "1994-005-25";
            String email = "epemxksl@gmail.com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(loginId, password, name, birthDay, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 8자 미만이면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordIsShorterThan8() {
            // arrange
            String loginId = "namjin123";
            String password = "qwer@12";
            String name = "namjin";
            String birthDay = "1994-05-25";
            String email = "epemxksl@gmail.com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(loginId, password, name, birthDay, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호가 16자 초과이면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordIsLongerThan16() {
            // arrange
            String loginId = "namjin123";
            String password = "qwer@123412341234";
            String name = "namjin";
            String birthDay = "1994-05-25";
            String email = "epemxksl@gmail.com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(loginId, password, name, birthDay, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 허용되지 않은 문자가 포함되면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsInvalidCharacters() {
            // arrange
            String loginId = "namjin123";
            String password = "qwer@1234한글";
            String name = "namjin";
            String birthDay = "1994-05-25";
            String email = "epemxksl@gmail.com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(loginId, password, name, birthDay, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("비밀번호에 생년월일이 포함되면, 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenPasswordContainsBirthDate() {
            // arrange
            String loginId = "namjin123";
            String password = "qwer@1994-05-25";
            String name = "namjin";
            String birthDay = "1994-05-25";
            String email = "epemxksl@gmail.com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                new UserModel(loginId, password, name, birthDay, email);
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

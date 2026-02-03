package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class UserTest {
    @DisplayName("회원 가입시, ")
    @Nested
    class Create {
        @DisplayName("loginId가 영문/숫자만 포함하면 정상적으로 생성된다.")
        @Test
        void createsUser_whenLoginIdIsAlphanumeric() {
            // arrange
            String loginId = "uniqueTester123";

            // act
            User user = UserFixture.builder()
                                   .loginId(loginId)
                                   .build();
            // assert
            assertAll(
                () -> assertThat(user.getLoginId()).isNotNull()
            );
        }

        @DisplayName("loginId에 영문/숫자 외의 문자가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenLoginIdIsNotAlphanumeric() {
            // arrange
            String loginId = "invalidId#?*";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserFixture.builder()
                           .loginId(loginId)
                           .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("이름이 공백 없는 문자열이면 정상적으로 생성된다.")
        @Test
        void createsUser_whenNameIsValid() {
            // arrange
            String name = "박자바";

            // act
            User user = UserFixture.builder()
                                   .name(name)
                                   .build();

            // assert
            assertThat(user.getName()).isEqualTo(name);
        }

        @DisplayName("name이 null이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsNull() {
            // arrange
            String name = null;

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserFixture.builder()
                           .name(name)
                           .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name이 빈 문자열이면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameIsEmpty() {
            // arrange
            String name = "";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserFixture.builder()
                           .name(name)
                           .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("name에 공백이 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenNameContainsWhitespace() {
            // arrange
            String name = "박 자바";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserFixture.builder()
                           .name(name)
                           .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("email이 @와 .을 포함하면 정상적으로 생성된다.")
        @Test
        void createsUser_whenEmailIsValid() {
            // arrange
            String email = "test@example.com";

            // act
            User user = UserFixture.builder()
                                   .email(email)
                                   .build();

            // assert
            assertThat(user.getEmail()).isEqualTo(email);
        }

        @DisplayName("email에 @가 없으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenEmailHasNoAtSign() {
            // arrange
            String email = "testexample.com";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserFixture.builder()
                           .email(email)
                           .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("email에 .이 없으면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenEmailHasNoDot() {
            // arrange
            String email = "test@examplecom";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserFixture.builder()
                           .email(email)
                           .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("birthDate가 YYYYMMDD 형식이면 정상적으로 생성된다.")
        @Test
        void createsUser_whenBirthDateIsValid() {
            // arrange
            String birthDate = "19900115";

            // act
            User user = UserFixture.builder()
                                   .birthDate(birthDate)
                                   .build();

            // assert
            assertThat(user.getBirthDate()).isEqualTo(birthDate);
        }

        @DisplayName("birthDate가 8자리가 아니면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBirthDateIsNot8Digits() {
            // arrange
            String birthDate = "1990011";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserFixture.builder()
                           .birthDate(birthDate)
                           .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @DisplayName("birthDate에 숫자가 아닌 문자가 포함되면 BAD_REQUEST 예외가 발생한다.")
        @Test
        void throwsBadRequestException_whenBirthDateContainsNonDigit() {
            // arrange
            String birthDate = "1990-01-15";

            // act
            CoreException result = assertThrows(CoreException.class, () -> {
                UserFixture.builder()
                           .birthDate(birthDate)
                           .build();
            });

            // assert
            assertThat(result.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }
    }
}

package com.loopers.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.loopers.support.error.CoreException;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordTest {

    @DisplayName("비밀번호 객체를 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("8~16자의 영문 대소문자, 숫자, 특수문자가 모두 포함되면 정상 생성된다.")
        @Test
        void createPassword_whenValidFormat() {
            // 대문자(V), 소문자(alid), 숫자(123), 특수문자(!@#) 모두 포함
            assertDoesNotThrow(() -> new Password("Valid123!@#"));
        }

        @DisplayName("규칙에 어긋나는 형식이면 예외가 발생한다.")
        @Test
        void createPassword_whenInvalidFormat() {
            assertThatThrownBy(() -> new Password("invalid"))
                    .isInstanceOf(CoreException.class);
        }
    }

    @DisplayName("비밀번호 비즈니스 규칙을 검증할 때, ")
    @Nested
    class Validation {

        @DisplayName("비밀번호에 생년월일(yyyyMMdd)이 포함되어 있으면 예외가 발생한다.")
        @Test
        void validateNotContainBirthday_fail() {
            // arrange: 정규식을 통과하기 위해 대문자 'P' 추가
            Password password = new Password("Pw19900115!");
            BirthDate birthDate = new BirthDate(LocalDate.of(1990, 1, 15));

            // act & assert
            assertThatThrownBy(() -> password.validateNotContainBirthday(birthDate))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("수정하려는 비밀번호가 기존 비밀번호와 동일하면 예외가 발생한다.")
        @Test
        void validateNotSameAs_fail() {
            // arrange
            Password currentPassword = new Password("Current123!");
            Password newPassword = new Password("Current123!");

            // act & assert
            assertThatThrownBy(() -> newPassword.validateNotSameAs(currentPassword))
                    .isInstanceOf(CoreException.class);
        }
    }
}

package com.loopers.interfaces.api;


import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class UserDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @DisplayName("이메일 검증")
    @Nested
    class EmailValidation {

        @Test
        @DisplayName("이메일 포맷이 맞으면 성공하는 테스트")
        void emailFormatSuccessTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "김용권",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("이메일 포맷이 안맞으면 실패하는 테스트")
        void emailFormatFailTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "김용권",
                "ykadasdad"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).hasSize(1);
        }

        @Test
        @DisplayName("이메일에 null이 들어오면 실패하는 테스트")
        void emailFormatNullTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "김용권",
                null
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).hasSize(1);
        }
    }

    @DisplayName("생년월일 검증")
    @Nested
    class BirthdayValidation {

        @Test
        @DisplayName("포맷이 맞으면 성공하는 테스트")
        void birthFormatSuccessTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "김용권",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("미래 날짜면 실패하는 테스트")
        void birthFormatDateIsFutureTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.now().plusDays(1),  // 내일
                "김용권",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("null이면 실패하는 테스트")
        void birthFormatDateIsNullTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                null,
                "김용권",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);;

            assertThat(violations).isNotEmpty();
        }
    }
}

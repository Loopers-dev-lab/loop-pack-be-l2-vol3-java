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
import org.junit.jupiter.api.Test;

public class UserDtoValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

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
}

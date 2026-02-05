package com.loopers.user.controller;

import com.loopers.user.dto.SignUpRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class SignUpRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @ParameterizedTest
    @MethodSource("필수값_누락_케이스")
    void 회원가입시_필수정보를_입력하지_않으면_실패한다(SignUpRequest request, String expectedField) {
        //given

        //when
        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        //then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo(expectedField);
    }

    static Stream<Arguments> 필수값_누락_케이스() {
        return Stream.of(
                Arguments.of(new SignUpRequest(null, "pw", "name", "19900101", "a@a.com"), "loginId"),
                Arguments.of(new SignUpRequest("test", null, "name", "19900101", "a@a.com"), "password"),
                Arguments.of(new SignUpRequest("test", "pw", null, "19900101", "a@a.com"), "name"),
                Arguments.of(new SignUpRequest("test", "pw", "name", null, "a@a.com"), "birthDate"),
                Arguments.of(new SignUpRequest("test", "pw", "name", "19900101", null), "email")
        );
    }

    @Test
    void 이메일_형식_불일치_시_실패() {
        //given
        String id = "test";
        String password = "pw";
        String name = "name";
        String birthDate = "19900101";
        String email = "test123";
        SignUpRequest request = new SignUpRequest(id, password, name, birthDate, email);

        //when
        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        //then
        assertThat(violations).hasSize(1);
        ConstraintViolation<SignUpRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("email");
        assertThat(violation.getConstraintDescriptor()
                .getAnnotation()
                .annotationType())
                .isEqualTo(Email.class);
    }

    @Test
    void 생년월일_형식_불일치_시_실패() {
        //given
        String id = "test";
        String password = "pw";
        String name = "name";
        String birthDate = "19900427";
        String email = "test123@test.com";
        SignUpRequest request = new SignUpRequest(id, password, name, birthDate, email);

        //when
        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        //then
        assertThat(violations).hasSize(1);
        ConstraintViolation<SignUpRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("birthDate");
    }
}

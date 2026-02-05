package com.loopers.user.controller;

import com.loopers.user.dto.CreateUserRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

public class CreateUserRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @ParameterizedTest
    @MethodSource("필수값_누락_케이스")
    void 회원가입시_필수정보를_입력하지_않으면_실패한다(CreateUserRequest request, String expectedField) {
        //given

        //when
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        //then
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo(expectedField);
    }

    static Stream<Arguments> 필수값_누락_케이스() {
        return Stream.of(
                Arguments.of(new CreateUserRequest(null, "pw", "name", "1990-01-01", "a@a.com"), "loginId"),
                Arguments.of(new CreateUserRequest("test", null, "name", "1990-01-01", "a@a.com"), "password"),
                Arguments.of(new CreateUserRequest("test", "pw", null, "1990-01-01", "a@a.com"), "name"),
                Arguments.of(new CreateUserRequest("test", "pw", "name", null, "a@a.com"), "birthDate"),
                Arguments.of(new CreateUserRequest("test", "pw", "name", "1990-01-01", null), "email")
        );
    }

    @Test
    void 이메일_형식_불일치_시_실패() {
        //given
        String id = "test";
        String password = "pw";
        String name = "name";
        String birthDate = "1990-01-01";
        String email = "test123";
        CreateUserRequest request = new CreateUserRequest(id, password, name, birthDate, email);

        //when
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        //then
        assertThat(violations).hasSize(1);
        ConstraintViolation<CreateUserRequest> violation = violations.iterator().next();
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
        CreateUserRequest request = new CreateUserRequest(id, password, name, birthDate, email);

        //when
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        //then
        assertThat(violations).hasSize(1);
        ConstraintViolation<CreateUserRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("birthDate");
    }

    @Test
    void 로그인ID에_영문_숫자_외_문자_포함_시_실패() {
        //given
        String id = "test@123";
        String password = "pw";
        String name = "name";
        String birthDate = "1990-01-01";
        String email = "test@test.com";
        CreateUserRequest request = new CreateUserRequest(id, password, name, birthDate, email);

        //when
        Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

        //then
        assertThat(violations).hasSize(1);
        ConstraintViolation<CreateUserRequest> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).isEqualTo("loginId");
        assertThat(violation.getConstraintDescriptor()
                .getAnnotation()
                .annotationType())
                .isEqualTo(Pattern.class);
    }
}

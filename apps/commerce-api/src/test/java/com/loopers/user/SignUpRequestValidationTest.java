package com.loopers.user;

import com.loopers.user.dto.SignUpRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class SignUpRequestValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    public void 필수값_누락시_실패() {
        //given
        SignUpRequest request = new SignUpRequest(
                null, //id
                null, //비밀번호
                null, //이름
                null, //생년월일
                null //이메일
        );

        //when
        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        //then
        assertThat(violations).hasSize(5);
    }

    @Test
    public void 이름이_비어있으면_실패() {
        //given
        SignUpRequest request = new SignUpRequest(
          "testId",
          "password123!",
          "    ",
          "19900427",
          "test@test.com"
        );

        //when
        Set<ConstraintViolation<SignUpRequest>> violations = validator.validate(request);

        //then
        assertThat(violations).hasSize(1);
        //실패한 필드 체크
        assertThat(violations.iterator().next().getPropertyPath().toString())
                .isEqualTo("name");
    }

    @Test
    public void 이메일_형식_불일치시_실패() {
        //given

        //when

        //then
    }

    @Test
    public void 생년월일_형식_불일치시_실패() {
        //given

        //when

        //then
    }
}

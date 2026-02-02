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

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
        }
    }

    @DisplayName("이름 검증")
    @Nested
    class NameValidation {

        @Test
        @DisplayName("올바른 한글 이름이면 검증에 통과한다")
        void validKoreanSuccessTest() {
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
        @DisplayName("올바른 영문 이름이면 검증에 통과한다")
        void validEnglishSuccessTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "John",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("한글과 영문이 섞인 이름이면 검증에 통과한다")
        void mixedKoreanAndEnglishSuccessTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "김John",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("공백이 포함된 이름이면 검증에 통과한다")
        void nameContainsSpaceSuccessTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "홍 길동",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("이름이 2자이면 검증에 통과한다")
        void nameIsMinLengthSuccessTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "김용",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("이름이 null이면 검증에 실패한다")
        void nameIsNullSuccessTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                null,
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 필수입니다.");
        }

        @Test
        @DisplayName("이름이 빈 문자열이면 검증에 실패한다")
        void nameIsEmptySuccessTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("이름이 공백만 있으면 검증에 실패한다")
        void nameFormatBlankTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "   ",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("이름이 1자이면 검증에 실패한다")
        void nameFormatTooShortTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "김",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 2자 이상 30자 이하여야 합니다.");
        }

        @Test
        @DisplayName("이름이 11자 이상이면 검증에 실패한다")
        void nameFormatTooLongTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "가나다라마바사아자차카",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 2자 이상 30자 이하여야 합니다.");
        }

        @Test
        @DisplayName("이름에 숫자가 포함되면 검증에 실패한다")
        void nameFormatContainsNumberTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "김용권1",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 한글, 영문, 공백만 입력 가능합니다.");
        }

        @Test
        @DisplayName("이름에 특수문자가 포함되면 검증에 실패한다")
        void nameFormatContainsSpecialCharacterTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "김용권!",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 한글, 영문, 공백만 입력 가능합니다.");
        }

        @Test
        @DisplayName("이름에 하이픈이 포함되면 검증에 실패한다")
        void nameFormatContainsHyphenTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "김-용권",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 한글, 영문, 공백만 입력 가능합니다.");
        }

        @Test
        @DisplayName("이름에 점이 포함되면 검증에 실패한다")
        void nameFormatContainsDotTest() {
            UserSignUpRequestDto dto = new UserSignUpRequestDto(
                "kim",
                "pw111",
                LocalDate.of(1991, 12, 3),
                "김.용권",
                "yk@google.com"
            );

            Set<ConstraintViolation<UserSignUpRequestDto>> violations = validator.validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 한글, 영문, 공백만 입력 가능합니다.");
        }
    }
}

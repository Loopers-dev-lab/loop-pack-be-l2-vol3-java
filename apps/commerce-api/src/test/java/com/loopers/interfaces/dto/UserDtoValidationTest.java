package com.loopers.interfaces.dto;


import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.interfaces.api.UsersSignUpRequestDto;
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

    private static final LocalDate DEFAULT_BIRTH_DATE = LocalDate.of(1991, 12, 3);
    private static final String DEFAULT_NAME = "김용권";
    private static final String DEFAULT_EMAIL = "yk@google.com";

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    private UsersSignUpRequestDto defaultDto() {
        return new UsersSignUpRequestDto(DEFAULT_BIRTH_DATE, DEFAULT_NAME, DEFAULT_EMAIL);
    }

    private UsersSignUpRequestDto dtoWithEmail(String email) {
        return new UsersSignUpRequestDto(DEFAULT_BIRTH_DATE, DEFAULT_NAME, email);
    }

    private UsersSignUpRequestDto dtoWithBirthDate(LocalDate birthDate) {
        return new UsersSignUpRequestDto(birthDate, DEFAULT_NAME, DEFAULT_EMAIL);
    }

    private UsersSignUpRequestDto dtoWithName(String name) {
        return new UsersSignUpRequestDto(DEFAULT_BIRTH_DATE, name, DEFAULT_EMAIL);
    }

    private Set<ConstraintViolation<UsersSignUpRequestDto>> validate(UsersSignUpRequestDto dto) {
        return validator.validate(dto);
    }

    @DisplayName("이메일 검증")
    @Nested
    class EmailValidation {

        @Test
        @DisplayName("이메일 포맷이 맞으면 성공하는 테스트")
        void emailFormatSuccessTest() {
            UsersSignUpRequestDto dto = defaultDto();

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("이메일 포맷이 안맞으면 실패하는 테스트")
        void emailFormatFailTest() {
            UsersSignUpRequestDto dto = dtoWithEmail("ykadasdad");

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).hasSize(1);
        }

        @Test
        @DisplayName("이메일에 null이 들어오면 실패하는 테스트")
        void emailFormatNullTest() {
            UsersSignUpRequestDto dto = dtoWithEmail(null);

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).hasSize(1);
        }
    }

    @DisplayName("생년월일 검증")
    @Nested
    class BirthdayValidation {

        @Test
        @DisplayName("포맷이 맞으면 성공하는 테스트")
        void birthFormatSuccessTest() {
            UsersSignUpRequestDto dto = defaultDto();

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("미래 날짜면 실패하는 테스트")
        void birthFormatDateIsFutureFailTest() {
            UsersSignUpRequestDto dto = dtoWithBirthDate(LocalDate.now().plusDays(1));

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("null이면 실패하는 테스트")
        void birthFormatDateIsNullFailTest() {
            UsersSignUpRequestDto dto = dtoWithBirthDate(null);

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isNotEmpty();
        }
    }

    @DisplayName("이름 검증")
    @Nested
    class NameValidation {

        @Test
        @DisplayName("올바른 한글 이름이면 검증에 통과한다")
        void validKoreanSuccessTest() {
            UsersSignUpRequestDto dto = defaultDto();

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("올바른 영문 이름이면 검증에 통과한다")
        void validEnglishSuccessTest() {
            UsersSignUpRequestDto dto = dtoWithName("John");

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("한글과 영문이 섞인 이름이면 검증에 통과한다")
        void mixedKoreanAndEnglishSuccessTest() {
            UsersSignUpRequestDto dto = dtoWithName("김John");

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("공백이 포함된 이름이면 검증에 통과한다")
        void nameContainsSpaceSuccessTest() {
            UsersSignUpRequestDto dto = dtoWithName("홍 길동");

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("이름이 2자이면 검증에 통과한다")
        void nameIsMinLengthSuccessTest() {
            UsersSignUpRequestDto dto = dtoWithName("김용");

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("이름이 null이면 검증에 실패한다")
        void nameIsNullFailTest() {
            UsersSignUpRequestDto dto = dtoWithName(null);

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 필수입니다.");
        }

        @Test
        @DisplayName("이름이 빈 문자열이면 검증에 실패한다")
        void nameIsEmptyFailTest() {
            UsersSignUpRequestDto dto = dtoWithName("");

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isNotEmpty();
        }

        @Test
        @DisplayName("이름이 공백만 있으면 검증에 실패한다")
        void nameFormatBlankFailTest() {
            UsersSignUpRequestDto dto = dtoWithName("   ");

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isNotEmpty();
            // NotBlank 또는 Pattern 위반 가능 (구현/순서에 따라 메시지 상이)
            assertThat(violations.iterator().next().getMessage())
                .isIn("이름은 필수입니다.", "이름은 한글, 영문, 공백만 입력 가능합니다.");
        }

        @Test
        @DisplayName("이름이 1자이면 검증에 실패한다")
        void nameFormatTooShortFailTest() {
            UsersSignUpRequestDto dto = dtoWithName("김");

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 2자 이상 30자 이하여야 합니다.");
        }

        @Test
        @DisplayName("이름이 11자 이상이면 검증에 실패한다")
        void nameFormatTooLongFailTest() {
            UsersSignUpRequestDto dto = dtoWithName("가나다라마바사아자차카");

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 2자 이상 30자 이하여야 합니다.");
        }

        @Test
        @DisplayName("이름에 숫자가 포함되면 검증에 실패한다")
        void nameFormatContainsNumberFailTest() {
            UsersSignUpRequestDto dto = dtoWithName("김용권1");

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 한글, 영문, 공백만 입력 가능합니다.");
        }

        @Test
        @DisplayName("이름에 특수문자가 포함되면 검증에 실패한다")
        void nameFormatContainsSpecialCharacterFailTest() {
            UsersSignUpRequestDto dto = dtoWithName("김용권!");

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 한글, 영문, 공백만 입력 가능합니다.");
        }

        @Test
        @DisplayName("이름에 하이픈이 포함되면 검증에 실패한다")
        void nameFormatContainsHyphenFailTest() {
            UsersSignUpRequestDto dto = dtoWithName("김-용권");

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 한글, 영문, 공백만 입력 가능합니다.");
        }

        @Test
        @DisplayName("이름에 점이 포함되면 검증에 실패한다")
        void nameFormatContainsDotFailTest() {
            UsersSignUpRequestDto dto = dtoWithName("김.용권");

            Set<ConstraintViolation<UsersSignUpRequestDto>> violations = validate(dto);

            assertThat(violations).isNotEmpty();
            assertThat(violations.iterator().next().getMessage()).isEqualTo("이름은 한글, 영문, 공백만 입력 가능합니다.");
        }
    }
}

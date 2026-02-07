package com.loopers.domain.user;

import com.loopers.domain.user.vo.BirthDate;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * BirthDate Value Object 단위 테스트
 *
 * 검증 규칙:
 * - YYYY-MM-DD 형식 (ISO 8601)
 * - 1900-01-01 ~ 현재 날짜
 * - 실제 존재하는 날짜
 * - 만 14세 이상
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
public class BirthDateTest {

    @DisplayName("생년월일을 생성할 때,")
    @Nested
    class Create {

        // ========== 정상 케이스 ==========

        @Test
        void 유효한_날짜이면_정상적으로_생성된다() {
            // arrange
            String value = "1994-11-15";

            // act
            BirthDate birthDate = new BirthDate(value);

            // assert
            assertThat(birthDate.getValue()).isEqualTo(LocalDate.of(1994, 11, 15));
        }

        @Test
        void 윤년_2월_29일이면_정상적으로_생성된다() {
            // arrange
            String value = "2000-02-29";

            // act
            BirthDate birthDate = new BirthDate(value);

            // assert
            assertThat(birthDate.getValue()).isEqualTo(LocalDate.of(2000, 2, 29));
        }

        @Test
        void 최소_허용_날짜이면_정상적으로_생성된다() {
            // arrange
            String value = "1900-01-01";

            // act
            BirthDate birthDate = new BirthDate(value);

            // assert
            assertThat(birthDate.getValue()).isEqualTo(LocalDate.of(1900, 1, 1));
        }

        @Test
        void 만_14세_경계이면_정상적으로_생성된다() {
            // arrange
            String value = LocalDate.now().minusYears(14).format(DateTimeFormatter.ISO_LOCAL_DATE);

            // act
            BirthDate birthDate = new BirthDate(value);

            // assert
            assertThat(birthDate.getValue()).isEqualTo(LocalDate.now().minusYears(14));
        }

        // ========== 엣지 케이스 ==========

        @Test
        void null이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate(null);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @Test
        void 잘못된_형식_슬래시이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate("1994/11/15");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @Test
        void 잘못된_형식_구분자_없음이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate("19941115");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @Test
        void 존재하지_않는_날짜이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate("1994-02-30");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @Test
        void 미래_날짜이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate("2030-01-01");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @Test
        void 날짜가_1900년_이전이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate("1899-12-31");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @Test
        void 만_14세_미만이면_예외가_발생한다() {
            // arrange
            String value = LocalDate.now().minusYears(13).format(DateTimeFormatter.ISO_LOCAL_DATE);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate(value);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @Test
        void 비윤년_2월_29일이면_예외가_발생한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate("1999-02-29");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @Test
        void 잘못된_형식이면_예외의_원인으로_DateTimeParseException을_포함한다() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate("1994/11/15");
            });

            assertThat(exception.getCause()).isInstanceOf(DateTimeParseException.class);
        }
    }
}

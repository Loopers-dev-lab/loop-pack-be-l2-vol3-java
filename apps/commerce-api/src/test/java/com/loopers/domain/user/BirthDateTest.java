package com.loopers.domain.user;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.UserErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

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
public class BirthDateTest {

    @DisplayName("생년월일을 생성할 때,")
    @Nested
    class Create {

        // ========== 정상 케이스 ==========

        @DisplayName("유효한 날짜이면, 정상적으로 생성된다.")
        @Test
        void createsBirthDate_whenValid() {
            // arrange
            String value = "1994-11-15";

            // act
            BirthDate birthDate = new BirthDate(value);

            // assert
            assertThat(birthDate.getValue()).isEqualTo(LocalDate.of(1994, 11, 15));
        }

        @DisplayName("윤년 2월 29일이면, 정상적으로 생성된다.")
        @Test
        void createsBirthDate_whenLeapYear() {
            // arrange
            String value = "2000-02-29";

            // act
            BirthDate birthDate = new BirthDate(value);

            // assert
            assertThat(birthDate.getValue()).isEqualTo(LocalDate.of(2000, 2, 29));
        }

        @DisplayName("최소 허용 날짜(1900-01-01)이면, 정상적으로 생성된다.")
        @Test
        void createsBirthDate_whenMinDate() {
            // arrange
            String value = "1900-01-01";

            // act
            BirthDate birthDate = new BirthDate(value);

            // assert
            assertThat(birthDate.getValue()).isEqualTo(LocalDate.of(1900, 1, 1));
        }

        @DisplayName("만 14세 경계(정확히 14세)이면, 정상적으로 생성된다.")
        @Test
        void createsBirthDate_whenExactlyMinAge() {
            // arrange
            String value = LocalDate.now().minusYears(14).format(DateTimeFormatter.ISO_LOCAL_DATE);

            // act
            BirthDate birthDate = new BirthDate(value);

            // assert
            assertThat(birthDate.getValue()).isEqualTo(LocalDate.now().minusYears(14));
        }

        // ========== 엣지 케이스 ==========

        @DisplayName("null이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNull() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate(null);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @DisplayName("잘못된 형식(슬래시)이면, 예외가 발생한다.")
        @Test
        void throwsException_whenSlashFormat() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate("1994/11/15");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @DisplayName("잘못된 형식(구분자 없음)이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNoSeparator() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate("19941115");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @DisplayName("존재하지 않는 날짜(2월 30일)이면, 예외가 발생한다.")
        @Test
        void throwsException_whenInvalidDate() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate("1994-02-30");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @DisplayName("미래 날짜이면, 예외가 발생한다.")
        @Test
        void throwsException_whenFutureDate() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate("2030-01-01");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @DisplayName("1900년 이전이면, 예외가 발생한다.")
        @Test
        void throwsException_whenTooOld() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate("1899-12-31");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @DisplayName("만 14세 미만이면, 예외가 발생한다.")
        @Test
        void throwsException_whenUnderMinAge() {
            // arrange
            String value = LocalDate.now().minusYears(13).format(DateTimeFormatter.ISO_LOCAL_DATE);

            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate(value);
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }

        @DisplayName("비윤년 2월 29일이면, 예외가 발생한다.")
        @Test
        void throwsException_whenNonLeapYearFeb29() {
            // act & assert
            CoreException exception = assertThrows(CoreException.class, () -> {
                new BirthDate("1999-02-29");
            });

            assertThat(exception.getErrorType()).isEqualTo(UserErrorType.INVALID_BIRTH_DATE);
        }
    }
}

package com.loopers.domain.user.vo;

import com.loopers.domain.user.exception.UserValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

public class BirthDateTest {

    @Nested
    @DisplayName("생년월일 형식 검증")
    class BirthDateValidationTest {

        @Test
        @DisplayName("유효한 생년월일 - 문자열")
        void validBirthDateString() {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> BirthDate.of("19990115"));
        }

        @Test
        @DisplayName("유효한 생년월일 - LocalDate")
        void validBirthDateLocalDate() {
            // when & then
            assertThatNoException()
                    .isThrownBy(() -> new BirthDate(LocalDate.of(1999, 1, 15)));
        }

        @ParameterizedTest
        @DisplayName("생년월일 형식 오류 - 빈 문자열 또는 null")
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void emptyOrNullBirthDate(String dateString) {
            // when & then
            assertThatThrownBy(() -> BirthDate.of(dateString))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("생년월일 형식 오류 - null LocalDate")
        void nullLocalDate() {
            // when & then
            assertThatThrownBy(() -> new BirthDate(null))
                    .isInstanceOf(UserValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("생년월일 형식 오류 - 잘못된 형식")
        @ValueSource(strings = {"1999-01-15", "99/01/15", "15011999", "abcdefgh"})
        void invalidFormat(String dateString) {
            // when & then
            assertThatThrownBy(() -> BirthDate.of(dateString))
                    .isInstanceOf(UserValidationException.class);
        }

        @ParameterizedTest
        @DisplayName("생년월일 형식 오류 - 존재하지 않는 날짜")
        @ValueSource(strings = {"19990230", "19991301", "19990132"})
        void invalidDate(String dateString) {
            // when & then
            assertThatThrownBy(() -> BirthDate.of(dateString))
                    .isInstanceOf(UserValidationException.class);
        }

        @Test
        @DisplayName("생년월일 형식 오류 - 미래 날짜")
        void futureDate() {
            // given
            LocalDate futureDate = LocalDate.now().plusDays(1);

            // when & then
            assertThatThrownBy(() -> new BirthDate(futureDate))
                    .isInstanceOf(UserValidationException.class);
        }
    }

    @Nested
    @DisplayName("생년월일 포맷 변환")
    class BirthDateFormatTest {

        @Test
        @DisplayName("YYMMDD 형식 변환")
        void toYymmdd() {
            // given
            BirthDate birthDate = BirthDate.of("19990115");

            // when
            String result = birthDate.toYymmdd();

            // then
            assertThat(result).isEqualTo("990115");
        }

        @Test
        @DisplayName("MMDD 형식 변환")
        void toMmdd() {
            // given
            BirthDate birthDate = BirthDate.of("19990115");

            // when
            String result = birthDate.toMmdd();

            // then
            assertThat(result).isEqualTo("0115");
        }

        @Test
        @DisplayName("DDMM 형식 변환")
        void toDdmm() {
            // given
            BirthDate birthDate = BirthDate.of("19990115");

            // when
            String result = birthDate.toDdmm();

            // then
            assertThat(result).isEqualTo("1501");
        }
    }
}

package com.loopers.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BirthdayTest {

    @Test
    @DisplayName("유효한 생년월일 생성 성공")
    void create_success() {
        // given
        LocalDate date = LocalDate.of(1990, 5, 15);

        // when
        Birthday birthday = Birthday.of(date);

        // then
        assertThat(birthday.getValue()).isEqualTo(date);
    }

    @Test
    @DisplayName("생년월일 null이면 예외")
    void create_fail_null() {
        // given
        LocalDate date = null;

        // when and then
        assertThatThrownBy(() -> Birthday.of(date))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("생년월일은 필수 입력값입니다.");
    }

    @Test
    @DisplayName("생년월일 미래 날짜면 예외")
    void create_fail_future() {
        // given
        LocalDate future = LocalDate.now().plusDays(1);

        // when and then
        assertThatThrownBy(() -> Birthday.of(future))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미래 날짜");
    }

    @Test
    @DisplayName("생년월일 1900년 이전이면 예외")
    void create_fail_before_1900() {
        // given
        LocalDate old = LocalDate.of(1899, 12, 31);

        // when and then
        assertThatThrownBy(() -> Birthday.of(old))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("1900년");
    }

    @Test
    @DisplayName("생년월일 오늘 날짜 가능")
    void create_success_today() {
        // given
        LocalDate today = LocalDate.now();

        // when
        Birthday birthday = Birthday.of(today);

        // then
        assertThat(birthday.getValue()).isEqualTo(today);
    }

    @Test
    @DisplayName("생년월일 1900-01-01 성공 (최소 경계)")
    void create_success_min_boundary() {
        LocalDate minDate = LocalDate.of(1900, 1, 1);
        Birthday birthday = Birthday.of(minDate);
        assertThat(birthday.getValue()).isEqualTo(minDate);
    }
}

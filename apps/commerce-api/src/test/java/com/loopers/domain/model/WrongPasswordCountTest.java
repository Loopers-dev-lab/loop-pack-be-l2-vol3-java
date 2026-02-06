package com.loopers.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WrongPasswordCountTest {

    @Test
    @DisplayName("초기값 0으로 생성")
    void init_success() {
        // given and when
        WrongPasswordCount count = WrongPasswordCount.init();

        // then
        assertThat(count.getValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("유효한 값으로 생성")
    void of_success() {
        // given
        int value = 3;

        // when
        WrongPasswordCount count = WrongPasswordCount.of(value);

        // then
        assertThat(count.getValue()).isEqualTo(3);
    }

    @Test
    @DisplayName("음수값이면 예외")
    void of_fail_negative() {
        // given
        int value = -1;

        // when and then
        assertThatThrownBy(() -> WrongPasswordCount.of(value))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("음수");
    }

    @Test
    @DisplayName("카운트 증가")
    void increment_success() {
        // given
        WrongPasswordCount count = WrongPasswordCount.init();

        // when
        WrongPasswordCount incremented = count.increment();

        // then
        assertThat(incremented.getValue()).isEqualTo(1);
    }

    @Test
    @DisplayName("카운트 리셋")
    void reset_success() {
        // given
        WrongPasswordCount count = WrongPasswordCount.of(3);

        // when
        WrongPasswordCount reset = count.reset();

        // then
        assertThat(reset.getValue()).isEqualTo(0);
    }

    @Test
    @DisplayName("5회 이상 실패시 잠금")
    void isLocked_true() {
        // given
        WrongPasswordCount count = WrongPasswordCount.of(5);

        // when
        boolean locked = count.isLocked();

        // then
        assertThat(locked).isTrue();
    }

    @Test
    @DisplayName("5회 미만 실패시 잠금 안됨")
    void isLocked_false() {
        // given
        WrongPasswordCount count = WrongPasswordCount.of(4);

        // when
        boolean locked = count.isLocked();

        // then
        assertThat(locked).isFalse();
    }
}

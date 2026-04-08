package com.loopers.domain.ranking;

import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingRequestDateTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Test
    @DisplayName("null이면 오늘(Asia/Seoul)을 반환한다.")
    void resolveOptionalYyyyMmDd_whenNull_shouldReturnTodaySeoul() {
        LocalDate expected = LocalDate.now(SEOUL);
        LocalDate actual = RankingRequestDate.resolveOptionalYyyyMmDd(null);
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    @DisplayName("유효한 yyyyMMdd면 해당 LocalDate를 반환한다.")
    void resolveOptionalYyyyMmDd_whenValid_shouldParse() {
        assertThat(RankingRequestDate.resolveOptionalYyyyMmDd("20260408")).isEqualTo(LocalDate.of(2026, 4, 8));
    }

    @Test
    @DisplayName("형식이 잘못되면 CoreException(BAD_REQUEST)")
    void resolveOptionalYyyyMmDd_whenInvalid_shouldThrow() {
        assertThatThrownBy(() -> RankingRequestDate.resolveOptionalYyyyMmDd("2026-04-08"))
                .isInstanceOf(CoreException.class);
    }
}

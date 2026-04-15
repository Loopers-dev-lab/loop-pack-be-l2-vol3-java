package com.loopers.batch.job.ranking.param;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

class RollingWindowResolverTest {

    @DisplayName("anchorDate 로부터 LAST_7D / LAST_30D 경계를 결정적으로 계산한다.")
    @Test
    void resolvesBoundariesDeterministically() {
        RollingWindow window = RollingWindowResolver.resolve("20260414");

        assertAll(
                () -> assertThat(window.anchorDate()).isEqualTo(LocalDate.of(2026, 4, 14)),
                () -> assertThat(window.anchorDateKey()).isEqualTo("20260414"),
                () -> assertThat(window.last7dStart()).isEqualTo(LocalDateTime.of(2026, 4, 8, 0, 0)),
                () -> assertThat(window.last7dEnd()).isEqualTo(LocalDateTime.of(2026, 4, 15, 0, 0)),
                () -> assertThat(window.last30dStart()).isEqualTo(LocalDateTime.of(2026, 3, 16, 0, 0)),
                () -> assertThat(window.last30dEnd()).isEqualTo(LocalDateTime.of(2026, 4, 15, 0, 0))
        );
    }

    @DisplayName("LAST_7D 구간은 anchor 포함 7일, LAST_30D 구간은 anchor 포함 30일이다.")
    @Test
    void windowSpansAreCorrect() {
        RollingWindow window = RollingWindowResolver.resolve("20260414");

        long last7dDays  = java.time.Duration.between(window.last7dStart(),  window.last7dEnd()).toDays();
        long last30dDays = java.time.Duration.between(window.last30dStart(), window.last30dEnd()).toDays();

        assertAll(
                () -> assertThat(last7dDays).isEqualTo(7),
                () -> assertThat(last30dDays).isEqualTo(30),
                () -> assertThat(window.last7dEnd()).isEqualTo(window.last30dEnd())
        );
    }

    @DisplayName("LAST_7D / LAST_30D 상한은 anchor + 1일 00:00 으로 '오늘은 제외' 된다.")
    @Test
    void endBoundaryExcludesToday() {
        RollingWindow window = RollingWindowResolver.resolve("20260414");

        LocalDateTime today0am = LocalDate.of(2026, 4, 15).atStartOfDay();
        assertAll(
                () -> assertThat(window.last7dEnd()).isEqualTo(today0am),
                () -> assertThat(window.last30dEnd()).isEqualTo(today0am)
        );
    }

    @DisplayName("월 경계를 걸쳐도 안전하게 계산된다 (음수 일자 없음).")
    @Test
    void handlesMonthCrossing() {
        RollingWindow window = RollingWindowResolver.resolve("20260102");

        assertAll(
                () -> assertThat(window.last7dStart()).isEqualTo(LocalDateTime.of(2025, 12, 27, 0, 0)),
                () -> assertThat(window.last30dStart()).isEqualTo(LocalDateTime.of(2025, 12, 4, 0, 0))
        );
    }

    @DisplayName("anchorDate 가 비어 있으면 예외를 던진다.")
    @Test
    void rejectsBlankAnchor() {
        assertAll(
                () -> assertThatThrownBy(() -> RollingWindowResolver.resolve(null))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> RollingWindowResolver.resolve(""))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> RollingWindowResolver.resolve("   "))
                        .isInstanceOf(IllegalArgumentException.class)
        );
    }

    @DisplayName("anchorDate 포맷이 yyyyMMdd 가 아니면 예외를 던진다.")
    @Test
    void rejectsMalformedAnchor() {
        assertAll(
                () -> assertThatThrownBy(() -> RollingWindowResolver.resolve("2026-04-14"))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> RollingWindowResolver.resolve("20260230"))
                        .isInstanceOf(IllegalArgumentException.class),
                () -> assertThatThrownBy(() -> RollingWindowResolver.resolve("abcdefgh"))
                        .isInstanceOf(IllegalArgumentException.class)
        );
    }
}

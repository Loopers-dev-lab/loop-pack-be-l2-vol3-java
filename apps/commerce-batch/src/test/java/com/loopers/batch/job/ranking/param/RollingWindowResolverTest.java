package com.loopers.batch.job.ranking.param;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RollingWindowResolverTest {

    @Nested
    class 경계_계산 {

        @Test
        void anchorDate_로부터_7D_30D_경계를_결정적으로_계산한다() {
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

        @Test
        void LAST_7D_는_7일_LAST_30D_는_30일_구간이다() {
            RollingWindow window = RollingWindowResolver.resolve("20260414");

            long last7dDays  = java.time.Duration.between(window.last7dStart(),  window.last7dEnd()).toDays();
            long last30dDays = java.time.Duration.between(window.last30dStart(), window.last30dEnd()).toDays();

            assertAll(
                    () -> assertThat(last7dDays).isEqualTo(7),
                    () -> assertThat(last30dDays).isEqualTo(30),
                    () -> assertThat(window.last7dEnd()).isEqualTo(window.last30dEnd())
            );
        }

        @Test
        void 상한은_anchor_다음날_00시로_오늘은_제외된다() {
            RollingWindow window = RollingWindowResolver.resolve("20260414");

            LocalDateTime today0am = LocalDate.of(2026, 4, 15).atStartOfDay();
            assertAll(
                    () -> assertThat(window.last7dEnd()).isEqualTo(today0am),
                    () -> assertThat(window.last30dEnd()).isEqualTo(today0am)
            );
        }

        @Test
        void 월_경계를_걸쳐도_안전하게_계산된다() {
            RollingWindow window = RollingWindowResolver.resolve("20260102");

            assertAll(
                    () -> assertThat(window.last7dStart()).isEqualTo(LocalDateTime.of(2025, 12, 27, 0, 0)),
                    () -> assertThat(window.last30dStart()).isEqualTo(LocalDateTime.of(2025, 12, 4, 0, 0))
            );
        }
    }

    @Nested
    class 입력_검증 {

        @Test
        void anchorDate_가_비어있으면_예외를_던진다() {
            assertAll(
                    () -> assertThatThrownBy(() -> RollingWindowResolver.resolve(null))
                            .isInstanceOf(IllegalArgumentException.class),
                    () -> assertThatThrownBy(() -> RollingWindowResolver.resolve(""))
                            .isInstanceOf(IllegalArgumentException.class),
                    () -> assertThatThrownBy(() -> RollingWindowResolver.resolve("   "))
                            .isInstanceOf(IllegalArgumentException.class)
            );
        }

        @Test
        void anchorDate_포맷이_yyyyMMdd_가_아니면_예외를_던진다() {
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
}

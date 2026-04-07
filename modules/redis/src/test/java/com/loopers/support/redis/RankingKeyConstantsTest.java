package com.loopers.support.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * uuuuMMdd / uuuuMMddHH 통일 후 컨트롤러(STRICT) 포맷과 streamer 포맷이 동일한지 검증.
 */
class RankingKeyConstantsTest {

    private static final DateTimeFormatter CONTROLLER_FORMAT =
        DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);

    @DisplayName("ISO week 경계(2026-12-28 월요일)에서 streamer dayKey와 컨트롤러 포맷이 일치한다.")
    @Test
    void weekYearBoundaryIsConsistent() {
        LocalDate boundary = LocalDate.of(2026, 12, 28);
        String streamer = RankingKeyConstants.dayKey(boundary);
        String controller = "ranking:day:" + boundary.format(CONTROLLER_FORMAT);
        assertThat(streamer).isEqualTo(controller);
        assertThat(streamer).endsWith("20261228");
    }

    @DisplayName("연말 12-31에서도 streamer dayKey가 컨트롤러 포맷과 일치한다.")
    @Test
    void newYearEveIsConsistent() {
        LocalDate eve = LocalDate.of(2026, 12, 31);
        String streamer = RankingKeyConstants.dayKey(eve);
        String controller = "ranking:day:" + eve.format(CONTROLLER_FORMAT);
        assertThat(streamer).isEqualTo(controller);
        assertThat(streamer).endsWith("20261231");
    }

    @DisplayName("hourKey도 uuuuMMddHH로 일관된다.")
    @Test
    void hourKeyFormat() {
        LocalDateTime t = LocalDateTime.of(2026, 12, 31, 23, 0);
        assertThat(RankingKeyConstants.hourKey(t)).isEqualTo("ranking:hour:2026123123");
    }

    @DisplayName("dayBucket/hourBucket 헬퍼는 prefix 없는 순수 bucket key를 반환한다.")
    @Test
    void bucketHelpers() {
        LocalDate d = LocalDate.of(2026, 4, 6);
        LocalDateTime t = LocalDateTime.of(2026, 4, 6, 13, 30);
        assertThat(RankingKeyConstants.dayBucket(d)).isEqualTo("20260406");
        assertThat(RankingKeyConstants.hourBucket(t)).isEqualTo("2026040613");
        assertThat(RankingKeyConstants.dayKey(d)).endsWith(RankingKeyConstants.dayBucket(d));
        assertThat(RankingKeyConstants.hourKey(t)).endsWith(RankingKeyConstants.hourBucket(t));
    }
}

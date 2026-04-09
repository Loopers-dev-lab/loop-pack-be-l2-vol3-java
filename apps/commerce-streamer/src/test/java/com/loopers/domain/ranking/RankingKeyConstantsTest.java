package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RankingKeyConstantsTest {

    @DisplayName("TTL을 계산할 때,")
    @Nested
    class CalculateTtlSeconds {

        @DisplayName("키 날짜 + 2일 자정까지의 초를 반환한다.")
        @Test
        void returnsTtlUntilAnchorMidnight() {
            // arrange
            LocalDate today = LocalDate.now();
            String key = RankingKeyConstants.KEY_PREFIX + today.format(DateTimeFormatter.BASIC_ISO_DATE);
            LocalDateTime expectedExpireAt = today.plusDays(2).atStartOfDay();

            // act
            long ttl = RankingKeyConstants.calculateTtlSeconds(key);

            // assert
            long expectedTtl = Duration.between(LocalDateTime.now(), expectedExpireAt).getSeconds();
            assertThat(ttl).isBetween(expectedTtl - 1, expectedTtl + 1);
        }

        @DisplayName("만료 시각이 지난 키는 0을 반환한다.")
        @Test
        void returnsZero_whenKeyDateIsExpired() {
            // arrange
            LocalDate pastDate = LocalDate.now().minusDays(3);
            String key = RankingKeyConstants.KEY_PREFIX + pastDate.format(DateTimeFormatter.BASIC_ISO_DATE);

            // act
            long ttl = RankingKeyConstants.calculateTtlSeconds(key);

            // assert
            assertThat(ttl).isZero();
        }
    }
}

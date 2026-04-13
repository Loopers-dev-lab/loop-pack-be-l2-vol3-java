package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RankingKeyConstantsTest {

    private static final DateTimeFormatter HOUR_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHH");

    @DisplayName("daily TTL을 계산할 때,")
    @Nested
    class CalculateDailyTtlSeconds {

        @DisplayName("키 날짜 + 2일 자정까지의 초를 반환한다.")
        @Test
        void returnsTtlUntilAnchorMidnight() {
            // arrange
            LocalDate today = LocalDate.now();
            String key = RankingKeyConstants.DAILY_KEY_PREFIX + today.format(DateTimeFormatter.BASIC_ISO_DATE);
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
            String key = RankingKeyConstants.DAILY_KEY_PREFIX + pastDate.format(DateTimeFormatter.BASIC_ISO_DATE);

            // act
            long ttl = RankingKeyConstants.calculateTtlSeconds(key);

            // assert
            assertThat(ttl).isZero();
        }
    }

    @DisplayName("hourly TTL을 계산할 때,")
    @Nested
    class CalculateHourlyTtlSeconds {

        @DisplayName("키 시간 + 2시간까지의 초를 반환한다.")
        @Test
        void returnsTtlUntilAnchorHour() {
            // arrange
            LocalDateTime currentHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
            String key = RankingKeyConstants.HOURLY_KEY_PREFIX + currentHour.format(HOUR_FORMAT);
            LocalDateTime expectedExpireAt = currentHour.plusHours(2);

            // act
            long ttl = RankingKeyConstants.calculateTtlSeconds(key);

            // assert
            long expectedTtl = Duration.between(LocalDateTime.now(), expectedExpireAt).getSeconds();
            assertThat(ttl).isBetween(expectedTtl - 1, expectedTtl + 1);
        }

        @DisplayName("만료 시각이 지난 키는 0을 반환한다.")
        @Test
        void returnsZero_whenKeyHourIsExpired() {
            // arrange
            LocalDateTime pastHour = LocalDateTime.now().minusHours(5).truncatedTo(ChronoUnit.HOURS);
            String key = RankingKeyConstants.HOURLY_KEY_PREFIX + pastHour.format(HOUR_FORMAT);

            // act
            long ttl = RankingKeyConstants.calculateTtlSeconds(key);

            // assert
            assertThat(ttl).isZero();
        }
    }

    @DisplayName("hourly 키를 생성할 때,")
    @Nested
    class HourlyKeyGeneration {

        @DisplayName("현재 시간 키는 yyyyMMddHH 형식의 현재 시간을 포함한다.")
        @Test
        void currentHourKeyContainsCurrentHour() {
            // arrange
            String expectedSuffix = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).format(HOUR_FORMAT);

            // act
            String key = RankingKeyConstants.currentHourKey();

            // assert
            assertThat(key).isEqualTo(RankingKeyConstants.HOURLY_KEY_PREFIX + expectedSuffix);
        }

        @DisplayName("다음 시간 키는 현재 시간 + 1시간을 포함한다.")
        @Test
        void nextHourKeyContainsNextHour() {
            // arrange
            String expectedSuffix = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
                    .plusHours(1).format(HOUR_FORMAT);

            // act
            String key = RankingKeyConstants.nextHourKey();

            // assert
            assertThat(key).isEqualTo(RankingKeyConstants.HOURLY_KEY_PREFIX + expectedSuffix);
        }
    }
}

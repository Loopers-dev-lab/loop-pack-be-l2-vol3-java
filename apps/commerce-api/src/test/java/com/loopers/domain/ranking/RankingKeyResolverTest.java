package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;

class RankingKeyResolverTest {

    @DisplayName("일간 키를 생성할 때,")
    @Nested
    class ResolveDaily {

        @DisplayName("날짜 문자열이 주어지면, ranking:v1:daily:{date} 형식의 키를 반환한다.")
        @Test
        void returnsKeyWithGivenDate() {
            // act
            String key = RankingKeyResolver.resolveDaily("20250406");

            // assert
            assertThat(key).isEqualTo("ranking:v1:daily:20250406");
        }

        @DisplayName("잘못된 형식의 날짜가 주어지면, 예외가 발생한다.")
        @Test
        void throwsException_whenInvalidDateFormat() {
            // act & assert
            assertThatThrownBy(() -> RankingKeyResolver.resolveDaily("2025-04-06"))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("null이 주어지면, 오늘 날짜 기준 키를 반환한다.")
        @Test
        void returnsKeyWithTodayDate_whenNull() {
            // arrange
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // act
            String key = RankingKeyResolver.resolveDaily(null);

            // assert
            assertThat(key).isEqualTo("ranking:v1:daily:" + today);
        }
    }

    @DisplayName("시간 단위 키를 생성할 때,")
    @Nested
    class ResolveHourly {

        @DisplayName("날짜시간 문자열이 주어지면, ranking:v1:hourly:{datetime} 형식의 키를 반환한다.")
        @Test
        void returnsKeyWithGivenDatetime() {
            // act
            String key = RankingKeyResolver.resolveHourly("2025040613");

            // assert
            assertThat(key).isEqualTo("ranking:v1:hourly:2025040613");
        }

        @DisplayName("잘못된 형식의 날짜시간이 주어지면, 예외가 발생한다.")
        @Test
        void throwsException_whenInvalidDatetimeFormat() {
            // act & assert
            assertThatThrownBy(() -> RankingKeyResolver.resolveHourly("2025-04-06"))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("유효하지 않은 시간(24 이상)이 주어지면, 예외가 발생한다.")
        @Test
        void throwsException_whenInvalidHour() {
            // act & assert
            assertThatThrownBy(() -> RankingKeyResolver.resolveHourly("2025040625"))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("null이 주어지면, 현재 시간 기준 키를 반환한다.")
        @Test
        void returnsKeyWithCurrentHour_whenNull() {
            // arrange
            String currentHour = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHH"));

            // act
            String key = RankingKeyResolver.resolveHourly(null);

            // assert
            assertThat(key).isEqualTo("ranking:v1:hourly:" + currentHour);
        }
    }
}

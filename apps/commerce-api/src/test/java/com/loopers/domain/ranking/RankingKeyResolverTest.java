package com.loopers.domain.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.loopers.support.error.CoreException;

class RankingKeyResolverTest {

    @DisplayName("Redis 키를 생성할 때,")
    @Nested
    class Resolve {

        @DisplayName("날짜 문자열이 주어지면, ranking:v1:all:{date} 형식의 키를 반환한다.")
        @Test
        void returnsKeyWithGivenDate() {
            // act
            String key = RankingKeyResolver.resolve("20250406");

            // assert
            assertThat(key).isEqualTo("ranking:v1:all:20250406");
        }

        @DisplayName("잘못된 형식의 날짜가 주어지면, 예외가 발생한다.")
        @Test
        void throwsException_whenInvalidDateFormat() {
            // act & assert
            assertThatThrownBy(() -> RankingKeyResolver.resolve("2025-04-06"))
                    .isInstanceOf(CoreException.class);
        }

        @DisplayName("null이 주어지면, 오늘 날짜 기준 키를 반환한다.")
        @Test
        void returnsKeyWithTodayDate_whenNull() {
            // arrange
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // act
            String key = RankingKeyResolver.resolve(null);

            // assert
            assertThat(key).isEqualTo("ranking:v1:all:" + today);
        }
    }

    @DisplayName("오늘 날짜 기준 Redis 키를 생성할 때,")
    @Nested
    class ResolveToday {

        @DisplayName("오늘 날짜를 yyyyMMdd 형식으로 포함한 키를 반환한다.")
        @Test
        void returnsKeyWithTodayDate() {
            // arrange
            String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

            // act
            String key = RankingKeyResolver.resolveToday();

            // assert
            assertThat(key).isEqualTo("ranking:v1:all:" + today);
        }
    }
}

package com.loopers.application.ranking;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("RankingKeyGenerator 테스트")
class RankingKeyGeneratorTest {

    private RankingKeyGenerator keyGenerator;
    private RankingProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RankingProperties(
            0.1,   // viewWeight
            0.2,   // likeWeight
            0.7,   // orderWeight
            "ranking:all:",  // keyPrefix
            2      // ttlDays
        );
        keyGenerator = new RankingKeyGenerator(properties);
    }

    @Nested
    @DisplayName("일별 키 생성")
    class DailyKey {

        @Test
        @DisplayName("특정 날짜의 키를 생성한다")
        void 특정_날짜_키_생성() {
            // Arrange
            LocalDate date = LocalDate.of(2025, 4, 7);

            // Act
            String key = keyGenerator.generateDailyKey(date);

            // Assert
            assertThat(key).isEqualTo("ranking:all:20250407");
        }

        @Test
        @DisplayName("현재 날짜의 키를 생성한다")
        void 현재_날짜_키_생성() {
            // Arrange
            LocalDate today = LocalDate.now();
            String expectedDate = today.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);

            // Act
            String key = keyGenerator.generateTodayKey();

            // Assert
            assertThat(key).isEqualTo("ranking:all:" + expectedDate);
        }
    }

    @Nested
    @DisplayName("TTL 조회")
    class TtlDays {

        @Test
        @DisplayName("설정된 TTL 일수를 반환한다")
        void TTL_일수_반환() {
            // Act
            int ttlDays = keyGenerator.getTtlDays();

            // Assert
            assertThat(ttlDays).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("키 파싱")
    class KeyParsing {

        @Test
        @DisplayName("키에서 날짜를 추출한다")
        void 키에서_날짜_추출() {
            // Arrange
            String key = "ranking:all:20250407";

            // Act
            LocalDate date = keyGenerator.parseDateFromKey(key);

            // Assert
            assertThat(date).isEqualTo(LocalDate.of(2025, 4, 7));
        }
    }
}

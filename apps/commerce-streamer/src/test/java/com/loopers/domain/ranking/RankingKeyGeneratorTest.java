package com.loopers.domain.ranking;

import com.loopers.event.ranking.RankingKeyGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class RankingKeyGeneratorTest {

    @DisplayName("keyOf()는 지정된 날짜 기반 랭킹 키를 반환한다.")
    @Test
    void keyOf_returnsKeyWithGivenDate() {
        // arrange
        LocalDate date = LocalDate.of(2025, 4, 9);

        // act
        String key = RankingKeyGenerator.keyOf(date);

        // assert
        assertThat(key).isEqualTo("ranking:all:20250409");
    }
}

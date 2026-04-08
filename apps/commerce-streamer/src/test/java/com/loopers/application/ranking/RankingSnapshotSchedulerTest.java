package com.loopers.application.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingSnapshotRepository;

@ExtendWith(MockitoExtension.class)
class RankingSnapshotSchedulerTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String TODAY_KEY = "ranking:v1:all:" + LocalDate.now().format(DATE_FORMAT);

    @InjectMocks
    private RankingSnapshotScheduler rankingSnapshotScheduler;

    @Mock
    private RankingRepository rankingRepository;

    @Mock
    private RankingSnapshotRepository rankingSnapshotRepository;

    @Captor
    private ArgumentCaptor<LocalDate> scoreDateCaptor;

    @Captor
    private ArgumentCaptor<Map<Long, Double>> productScoresCaptor;

    @DisplayName("스냅샷을 수행할 때,")
    @Nested
    class TakeSnapshot {

        @DisplayName("Redis에 데이터가 있으면, DB에 스냅샷한다.")
        @Test
        void savesToDB_whenRedisHasData() {
            // arrange
            given(rankingRepository.readTopScores(TODAY_KEY, 100))
                    .willReturn(Map.of("1", 45.3, "2", 30.1));

            // act
            rankingSnapshotScheduler.takeSnapshot();

            // assert
            then(rankingSnapshotRepository).should()
                    .saveAll(scoreDateCaptor.capture(), productScoresCaptor.capture());

            assertThat(scoreDateCaptor.getValue()).isEqualTo(LocalDate.now());

            Map<Long, Double> captured = productScoresCaptor.getValue();
            assertThat(captured).hasSize(2);
            assertThat(captured.get(1L)).isEqualTo(45.3);
            assertThat(captured.get(2L)).isEqualTo(30.1);
        }

        @DisplayName("Redis에 데이터가 없으면, DB 저장을 호출하지 않는다.")
        @Test
        void skips_whenRedisIsEmpty() {
            // arrange
            given(rankingRepository.readTopScores(TODAY_KEY, 100))
                    .willReturn(Collections.emptyMap());

            // act
            rankingSnapshotScheduler.takeSnapshot();

            // assert
            then(rankingSnapshotRepository).should(times(0))
                    .saveAll(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyMap());
        }

        @DisplayName("readTopScores의 String key가 Long productId로 올바르게 변환된다.")
        @Test
        void convertsStringKeyToLongProductId() {
            // arrange
            given(rankingRepository.readTopScores(anyString(), anyInt()))
                    .willReturn(Map.of("123", 10.0, "456", 20.0));

            // act
            rankingSnapshotScheduler.takeSnapshot();

            // assert
            then(rankingSnapshotRepository).should()
                    .saveAll(scoreDateCaptor.capture(), productScoresCaptor.capture());

            Map<Long, Double> captured = productScoresCaptor.getValue();
            assertThat(captured).containsOnlyKeys(123L, 456L);
        }
    }
}

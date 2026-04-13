package com.loopers.application.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.ranking.RankingKeyConstants;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScore;
import com.loopers.domain.ranking.RankingSnapshotRepository;

@ExtendWith(MockitoExtension.class)
class RankingSnapshotSchedulerTest {

    private static final String CURRENT_HOUR_KEY = RankingKeyConstants.currentHourKey();

    @InjectMocks
    private RankingSnapshotScheduler rankingSnapshotScheduler;

    @Mock
    private RankingRepository rankingRepository;

    @Mock
    private RankingSnapshotRepository rankingSnapshotRepository;

    @Captor
    private ArgumentCaptor<LocalDateTime> scoreHourCaptor;

    @Captor
    private ArgumentCaptor<List<RankingScore>> scoresCaptor;

    @DisplayName("스냅샷을 수행할 때,")
    @Nested
    class TakeSnapshot {

        @DisplayName("Redis에 데이터가 있으면, DB에 시간 단위로 스냅샷한다.")
        @Test
        void savesToDB_whenRedisHasData() {
            // arrange
            given(rankingRepository.readTopScores(CURRENT_HOUR_KEY, 100))
                    .willReturn(List.of(new RankingScore(1L, 45.3), new RankingScore(2L, 30.1)));

            // act
            rankingSnapshotScheduler.takeSnapshot();

            // assert
            then(rankingSnapshotRepository).should()
                    .saveAll(scoreHourCaptor.capture(), scoresCaptor.capture());

            assertThat(scoreHourCaptor.getValue())
                    .isEqualTo(LocalDateTime.now().truncatedTo(ChronoUnit.HOURS));

            List<RankingScore> captured = scoresCaptor.getValue();
            assertThat(captured).hasSize(2);
            assertThat(captured).extracting(RankingScore::productId).containsExactly(1L, 2L);
            assertThat(captured).extracting(RankingScore::score).containsExactly(45.3, 30.1);
        }

        @DisplayName("Redis에 데이터가 없으면, DB 저장을 호출하지 않는다.")
        @Test
        void skips_whenRedisIsEmpty() {
            // arrange
            given(rankingRepository.readTopScores(CURRENT_HOUR_KEY, 100))
                    .willReturn(Collections.emptyList());

            // act
            rankingSnapshotScheduler.takeSnapshot();

            // assert
            then(rankingSnapshotRepository).should(times(0))
                    .saveAll(any(), anyList());
        }
    }
}

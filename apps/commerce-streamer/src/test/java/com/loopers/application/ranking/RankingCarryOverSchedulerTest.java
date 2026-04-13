package com.loopers.application.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

@ExtendWith(MockitoExtension.class)
class RankingCarryOverSchedulerTest {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String TODAY_KEY = "ranking:v1:daily:" + LocalDate.now().format(DATE_FORMAT);
    private static final String TOMORROW_KEY = "ranking:v1:daily:" + LocalDate.now().plusDays(1).format(DATE_FORMAT);
    private static final String CURRENT_HOUR_KEY = RankingKeyConstants.currentHourKey();
    private static final String NEXT_HOUR_KEY = RankingKeyConstants.nextHourKey();

    @InjectMocks
    private RankingCarryOverScheduler rankingCarryOverScheduler;

    @Mock
    private RankingRepository rankingRepository;

    @Captor
    private ArgumentCaptor<List<RankingScore>> scoresCaptor;

    @Captor
    private ArgumentCaptor<Long> ttlCaptor;

    @DisplayName("일간 이월을 수행할 때,")
    @Nested
    class CarryOverDaily {

        @DisplayName("오늘 상위 스코어에 감쇠 계수(0.01)를 적용하여 내일 키에 이월한다.")
        @Test
        void carriesOverWithDecayFactor() {
            // arrange
            given(rankingRepository.exists(TOMORROW_KEY)).willReturn(false);
            given(rankingRepository.readTopScores(TODAY_KEY, 200))
                    .willReturn(List.of(new RankingScore(1L, 100.0), new RankingScore(2L, 50.0)));

            // act
            rankingCarryOverScheduler.carryOverDaily();

            // assert
            then(rankingRepository).should().addScores(eq(TOMORROW_KEY), scoresCaptor.capture(), ttlCaptor.capture());
            List<RankingScore> decayed = scoresCaptor.getValue();
            assertThat(decayed).hasSize(2);
            assertThat(findByProductId(decayed, 1L).score()).isCloseTo(1.0, offset(0.001));
            assertThat(findByProductId(decayed, 2L).score()).isCloseTo(0.5, offset(0.001));
            assertThat(ttlCaptor.getValue()).isPositive();
        }

        @DisplayName("내일 키가 이미 존재하면, 스킵한다.")
        @Test
        void skips_whenTomorrowKeyAlreadyExists() {
            // arrange
            given(rankingRepository.exists(TOMORROW_KEY)).willReturn(true);

            // act
            rankingCarryOverScheduler.carryOverDaily();

            // assert
            then(rankingRepository).should(never()).readTopScores(anyString(), anyInt());
            then(rankingRepository).should(never()).addScores(anyString(), anyList(), anyLong());
        }

        @DisplayName("오늘 랭킹 데이터가 없으면, 이월하지 않는다.")
        @Test
        void skips_whenNoTodayData() {
            // arrange
            given(rankingRepository.exists(TOMORROW_KEY)).willReturn(false);
            given(rankingRepository.readTopScores(TODAY_KEY, 200)).willReturn(Collections.emptyList());

            // act
            rankingCarryOverScheduler.carryOverDaily();

            // assert
            then(rankingRepository).should(times(0)).addScores(anyString(), anyList(), anyLong());
        }
    }

    @DisplayName("시간 단위 이월을 수행할 때,")
    @Nested
    class CarryOverHourly {

        @DisplayName("현재 시간 스코어를 감쇠(0.01)하여 다음 시간 키에 이월한다.")
        @Test
        void decaysAndCarriesOverToNextHour() {
            // arrange
            given(rankingRepository.exists(NEXT_HOUR_KEY)).willReturn(false);
            given(rankingRepository.readTopScores(CURRENT_HOUR_KEY, 200))
                    .willReturn(List.of(new RankingScore(1L, 100.0), new RankingScore(2L, 50.0)));

            // act
            rankingCarryOverScheduler.carryOverHourly();

            // assert
            then(rankingRepository).should().addScores(eq(NEXT_HOUR_KEY), scoresCaptor.capture(), anyLong());
            List<RankingScore> decayed = scoresCaptor.getValue();
            assertThat(decayed).hasSize(2);
            assertThat(findByProductId(decayed, 1L).score()).isCloseTo(1.0, offset(0.001));
            assertThat(findByProductId(decayed, 2L).score()).isCloseTo(0.5, offset(0.001));
        }

        @DisplayName("다음 시간 키가 이미 존재하면, 스킵한다.")
        @Test
        void skips_whenNextHourKeyExists() {
            // arrange
            given(rankingRepository.exists(NEXT_HOUR_KEY)).willReturn(true);

            // act
            rankingCarryOverScheduler.carryOverHourly();

            // assert
            then(rankingRepository).should(never()).readTopScores(anyString(), anyInt());
            then(rankingRepository).should(never()).addScores(anyString(), anyList(), anyLong());
        }

        @DisplayName("현재 시간 데이터가 없으면, 스킵한다.")
        @Test
        void skips_whenNoCurrentHourData() {
            // arrange
            given(rankingRepository.exists(NEXT_HOUR_KEY)).willReturn(false);
            given(rankingRepository.readTopScores(CURRENT_HOUR_KEY, 200)).willReturn(Collections.emptyList());

            // act
            rankingCarryOverScheduler.carryOverHourly();

            // assert
            then(rankingRepository).should(times(0)).addScores(anyString(), anyList(), anyLong());
        }
    }

    private RankingScore findByProductId(List<RankingScore> scores, Long productId) {
        return scores.stream()
                .filter(s -> s.productId().equals(productId))
                .findFirst()
                .orElseThrow();
    }
}

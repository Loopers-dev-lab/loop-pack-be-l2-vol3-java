package com.loopers.application.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.times;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.ranking.ProductRankingRepository;
import com.loopers.domain.ranking.RankingEvent;
import com.loopers.domain.ranking.RankingScoreCalculator;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    private static final String TODAY_KEY = "ranking:v1:all:"
            + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

    @InjectMocks
    private RankingService rankingService;

    @Mock
    private EventHandledRepository eventHandledRepository;

    @Mock
    private RankingScoreCalculator scoreCalculator;

    @Mock
    private ProductRankingRepository productRankingRepository;

    @Captor
    private ArgumentCaptor<Map<Long, Double>> scoresCaptor;

    @DisplayName("배치를 처리할 때,")
    @Nested
    class ProcessBatch {

        @DisplayName("조회 이벤트의 score를 productId별로 합산한다.")
        @Test
        void aggregatesViewScores() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(true);
            given(scoreCalculator.calculateViewScore()).willReturn(0.1);
            List<RankingEvent> events = List.of(
                    new RankingEvent.View("e1", 1L),
                    new RankingEvent.View("e2", 1L),
                    new RankingEvent.View("e3", 2L)
            );

            // act
            rankingService.processBatch(events);

            // assert
            then(productRankingRepository).should().incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            Map<Long, Double> scores = scoresCaptor.getValue();
            assertThat(scores.get(1L)).isCloseTo(0.2, offset(0.001));
            assertThat(scores.get(2L)).isCloseTo(0.1, offset(0.001));
        }

        @DisplayName("좋아요/취소 이벤트의 score를 합산한다.")
        @Test
        void aggregatesLikeScores() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(true);
            given(scoreCalculator.calculateLikeScore(true)).willReturn(0.2);
            given(scoreCalculator.calculateLikeScore(false)).willReturn(-0.2);
            List<RankingEvent> events = List.of(
                    new RankingEvent.Like("e1", 1L, true),
                    new RankingEvent.Like("e2", 1L, true),
                    new RankingEvent.Like("e3", 1L, false)
            );

            // act
            rankingService.processBatch(events);

            // assert
            then(productRankingRepository).should().incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue().get(1L)).isCloseTo(0.2, offset(0.001));
        }

        @DisplayName("주문 이벤트의 score를 합산한다.")
        @Test
        void aggregatesOrderScores() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(true);
            given(scoreCalculator.calculateOrderScore(50000L, 1L)).willReturn(2.82);
            given(scoreCalculator.calculateOrderScore(50000L, 2L)).willReturn(3.0);
            List<RankingEvent> events = List.of(
                    new RankingEvent.Order("e1", 1L, 50000L, 1L),
                    new RankingEvent.Order("e2", 1L, 50000L, 2L)
            );

            // act
            rankingService.processBatch(events);

            // assert
            then(productRankingRepository).should().incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue().get(1L)).isCloseTo(5.82, offset(0.001));
        }

        @DisplayName("혼합 이벤트를 단일 Pipeline으로 처리한다.")
        @Test
        void aggregatesMixedEvents() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(true);
            given(scoreCalculator.calculateViewScore()).willReturn(0.1);
            given(scoreCalculator.calculateLikeScore(true)).willReturn(0.2);
            given(scoreCalculator.calculateOrderScore(50000L, 1L)).willReturn(2.82);
            List<RankingEvent> events = List.of(
                    new RankingEvent.View("e1", 1L),
                    new RankingEvent.Like("e2", 1L, true),
                    new RankingEvent.Order("e3", 1L, 50000L, 1L)
            );

            // act
            rankingService.processBatch(events);

            // assert
            then(productRankingRepository).should(times(1)).incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue().get(1L)).isCloseTo(3.12, offset(0.001));
        }

        @DisplayName("빈 리스트이면, Repository를 호출하지 않는다.")
        @Test
        void skips_whenEmpty() {
            // act
            rankingService.processBatch(List.of());

            // assert
            then(productRankingRepository).should(times(0)).incrementScores(anyString(), anyMap());
        }

        @DisplayName("중복 이벤트는 skip한다.")
        @Test
        void skipsDuplicateEvents() {
            // arrange
            given(eventHandledRepository.markIfAbsent("e1")).willReturn(true);
            given(eventHandledRepository.markIfAbsent("e2")).willReturn(false);
            given(scoreCalculator.calculateViewScore()).willReturn(0.1);
            List<RankingEvent> events = List.of(
                    new RankingEvent.View("e1", 1L),
                    new RankingEvent.View("e2", 1L)
            );

            // act
            rankingService.processBatch(events);

            // assert — e2는 skip되어 score가 0.1만 반영
            then(productRankingRepository).should().incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue().get(1L)).isCloseTo(0.1, offset(0.001));
        }

        @DisplayName("좋아요와 취소가 상쇄되면, 해당 productId를 scores에서 제외한다.")
        @Test
        void excludesZeroScoreProducts() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(true);
            given(scoreCalculator.calculateLikeScore(true)).willReturn(0.2);
            given(scoreCalculator.calculateLikeScore(false)).willReturn(-0.2);
            given(scoreCalculator.calculateViewScore()).willReturn(0.1);
            List<RankingEvent> events = List.of(
                    new RankingEvent.Like("e1", 1L, true),
                    new RankingEvent.Like("e2", 1L, false),
                    new RankingEvent.View("e3", 2L)
            );

            // act
            rankingService.processBatch(events);

            // assert — productId=1은 0.0이므로 제외, productId=2만 포함
            then(productRankingRepository).should().incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            Map<Long, Double> scores = scoresCaptor.getValue();
            assertThat(scores).doesNotContainKey(1L);
            assertThat(scores.get(2L)).isCloseTo(0.1, offset(0.001));
        }

        @DisplayName("모든 이벤트가 중복이면, Repository를 호출하지 않는다.")
        @Test
        void skipsAll_whenAllDuplicate() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(false);
            List<RankingEvent> events = List.of(
                    new RankingEvent.View("e1", 1L),
                    new RankingEvent.View("e2", 2L)
            );

            // act
            rankingService.processBatch(events);

            // assert
            then(productRankingRepository).should(times(0)).incrementScores(anyString(), anyMap());
        }
    }
}

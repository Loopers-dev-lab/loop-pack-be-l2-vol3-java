package com.loopers.application.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.data.Offset.offset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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

import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.ranking.RankingEvent;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingScore;
import com.loopers.domain.ranking.RankingScoreCalculator;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    private static final String TODAY_KEY = "ranking:v1:daily:"
            + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    private static final String TOMORROW_KEY = "ranking:v1:daily:"
            + LocalDate.now().plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);
    private static final String CURRENT_HOUR_KEY = "ranking:v1:hourly:"
            + LocalDateTime.now().truncatedTo(ChronoUnit.HOURS)
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
    private static final String NEXT_HOUR_KEY = "ranking:v1:hourly:"
            + LocalDateTime.now().truncatedTo(ChronoUnit.HOURS).plusHours(1)
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHH"));

    @InjectMocks
    private RankingService rankingService;

    @Mock
    private EventHandledRepository eventHandledRepository;

    @Mock
    private RankingScoreCalculator scoreCalculator;

    @Mock
    private RankingRepository rankingRepository;

    @Captor
    private ArgumentCaptor<List<RankingScore>> scoresCaptor;

    @DisplayName("일간 배치를 처리할 때,")
    @Nested
    class ProcessDailyBatch {

        @DisplayName("조회 이벤트의 score를 productId별로 합산한다.")
        @Test
        void aggregatesViewScores() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(true);
            given(scoreCalculator.calculate(any(RankingEvent.View.class)))
                    .willReturn(new RankingScore(1L, 0.1), new RankingScore(1L, 0.1), new RankingScore(2L, 0.1));
            List<RankingEvent> events = List.of(
                    new RankingEvent.View("e1", 1L),
                    new RankingEvent.View("e2", 1L),
                    new RankingEvent.View("e3", 2L)
            );

            // act
            rankingService.processDailyBatch(events);

            // assert
            then(rankingRepository).should().incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue()).extracting(RankingScore::productId, RankingScore::score)
                    .containsExactlyInAnyOrder(tuple(1L, 0.2), tuple(2L, 0.1));
        }

        @DisplayName("혼합 이벤트를 단일 Pipeline으로 처리한다.")
        @Test
        void aggregatesMixedEvents() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(true);
            RankingEvent.View view = new RankingEvent.View("e1", 1L);
            RankingEvent.Like like = new RankingEvent.Like("e2", 1L, true);
            RankingEvent.Order order = new RankingEvent.Order("e3", List.of(
                    new RankingEvent.Order.OrderItem(1L, 50000L, 1L)));
            given(scoreCalculator.calculate(view)).willReturn(new RankingScore(1L, 0.1));
            given(scoreCalculator.calculate(like)).willReturn(new RankingScore(1L, 0.2));
            given(scoreCalculator.calculate(order)).willReturn(List.of(new RankingScore(1L, 2.82)));

            // act
            rankingService.processDailyBatch(List.of(view, like, order));

            // assert
            then(rankingRepository).should(times(1)).incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue()).singleElement()
                    .satisfies(s -> assertThat(s.score()).isCloseTo(3.12, offset(0.001)));
        }

        @DisplayName("빈 리스트이면, Repository를 호출하지 않는다.")
        @Test
        void skips_whenEmpty() {
            rankingService.processDailyBatch(Collections.emptyList());
            then(rankingRepository).should(times(0)).incrementScores(anyString(), anyList());
        }

        @DisplayName("중복 이벤트는 skip한다.")
        @Test
        void skipsDuplicateEvents() {
            // arrange
            given(eventHandledRepository.markIfAbsent("daily:e1")).willReturn(true);
            given(eventHandledRepository.markIfAbsent("daily:e2")).willReturn(false);
            RankingEvent.View view1 = new RankingEvent.View("e1", 1L);
            given(scoreCalculator.calculate(view1)).willReturn(new RankingScore(1L, 0.1));

            // act
            rankingService.processDailyBatch(List.of(view1, new RankingEvent.View("e2", 1L)));

            // assert
            then(rankingRepository).should().incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue()).singleElement()
                    .satisfies(s -> assertThat(s.score()).isCloseTo(0.1, offset(0.001)));
        }

        @DisplayName("좋아요와 취소가 상쇄되면, 해당 productId를 scores에서 제외한다.")
        @Test
        void excludesZeroScoreProducts() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(true);
            RankingEvent.Like like = new RankingEvent.Like("e1", 1L, true);
            RankingEvent.Like unlike = new RankingEvent.Like("e2", 1L, false);
            RankingEvent.View view = new RankingEvent.View("e3", 2L);
            given(scoreCalculator.calculate(like)).willReturn(new RankingScore(1L, 0.2));
            given(scoreCalculator.calculate(unlike)).willReturn(new RankingScore(1L, -0.2));
            given(scoreCalculator.calculate(view)).willReturn(new RankingScore(2L, 0.1));

            // act
            rankingService.processDailyBatch(List.of(like, unlike, view));

            // assert
            then(rankingRepository).should().incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue()).extracting(RankingScore::productId).doesNotContain(1L);
            assertThat(scoresCaptor.getValue()).singleElement()
                    .satisfies(s -> assertThat(s.productId()).isEqualTo(2L));
        }

        @DisplayName("모든 이벤트가 중복이면, Repository를 호출하지 않는다.")
        @Test
        void skipsAll_whenAllDuplicate() {
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(false);
            rankingService.processDailyBatch(List.of(
                    new RankingEvent.View("e1", 1L), new RankingEvent.View("e2", 2L)));
            then(rankingRepository).should(times(0)).incrementScores(anyString(), anyList());
        }
    }

    @DisplayName("일간 상품을 랭킹에서 제거할 때,")
    @Nested
    class RemoveDailyProducts {

        @DisplayName("멱등성 체크 후 오늘과 내일 키에서 해당 상품을 제거한다.")
        @Test
        void removesProductFromTodayAndTomorrowKey() {
            given(eventHandledRepository.markIfAbsent("daily:e1")).willReturn(true);
            rankingService.removeDailyProducts(List.of(new RankingEvent.Delete("e1", 5L)));
            then(rankingRepository).should().removeMembers(eq(TODAY_KEY), eq(List.of(5L)));
            then(rankingRepository).should().removeMembers(eq(TOMORROW_KEY), eq(List.of(5L)));
        }

        @DisplayName("모든 이벤트가 중복이면, Repository를 호출하지 않는다.")
        @Test
        void removesNothing_whenAllDuplicate() {
            given(eventHandledRepository.markIfAbsent("daily:e1")).willReturn(false);
            rankingService.removeDailyProducts(List.of(new RankingEvent.Delete("e1", 5L)));
            then(rankingRepository).should(never()).removeMembers(anyString(), anyList());
        }

        @DisplayName("빈 목록이면, Repository를 호출하지 않는다.")
        @Test
        void skips_whenEmpty() {
            rankingService.removeDailyProducts(Collections.emptyList());
            then(rankingRepository).should(never()).removeMembers(anyString(), anyList());
        }
    }

    @DisplayName("시간 단위 배치를 처리할 때,")
    @Nested
    class ProcessHourlyBatch {

        @DisplayName("현재 시간 hourly 키에 점수를 적재한다.")
        @Test
        void incrementsScoresToCurrentHourKey() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(true);
            given(scoreCalculator.calculate(any(RankingEvent.View.class)))
                    .willReturn(new RankingScore(1L, 0.1));

            // act
            rankingService.processHourlyBatch(List.of(new RankingEvent.View("e1", 1L)));

            // assert
            then(rankingRepository).should().incrementScores(eq(CURRENT_HOUR_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue()).singleElement()
                    .satisfies(s -> assertThat(s.score()).isCloseTo(0.1, offset(0.001)));
        }

        @DisplayName("혼합 이벤트를 상품별로 합산한다.")
        @Test
        void aggregatesMixedEvents() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(true);
            RankingEvent.View view = new RankingEvent.View("e1", 1L);
            RankingEvent.Like like = new RankingEvent.Like("e2", 1L, true);
            RankingEvent.Order order = new RankingEvent.Order("e3", List.of(
                    new RankingEvent.Order.OrderItem(1L, 50000L, 1L)));
            given(scoreCalculator.calculate(view)).willReturn(new RankingScore(1L, 0.1));
            given(scoreCalculator.calculate(like)).willReturn(new RankingScore(1L, 0.2));
            given(scoreCalculator.calculate(order)).willReturn(List.of(new RankingScore(1L, 2.82)));

            // act
            rankingService.processHourlyBatch(List.of(view, like, order));

            // assert
            then(rankingRepository).should(times(1)).incrementScores(eq(CURRENT_HOUR_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue()).singleElement()
                    .satisfies(s -> assertThat(s.score()).isCloseTo(3.12, offset(0.001)));
        }

        @DisplayName("빈 리스트이면, Repository를 호출하지 않는다.")
        @Test
        void skips_whenEmpty() {
            rankingService.processHourlyBatch(Collections.emptyList());
            then(rankingRepository).should(times(0)).incrementScores(anyString(), anyList());
        }

        @DisplayName("중복 이벤트는 skip한다.")
        @Test
        void skipsDuplicateEvents() {
            given(eventHandledRepository.markIfAbsent("hourly:e1")).willReturn(true);
            given(eventHandledRepository.markIfAbsent("hourly:e2")).willReturn(false);
            RankingEvent.View view1 = new RankingEvent.View("e1", 1L);
            given(scoreCalculator.calculate(view1)).willReturn(new RankingScore(1L, 0.1));

            rankingService.processHourlyBatch(List.of(view1, new RankingEvent.View("e2", 1L)));

            then(rankingRepository).should().incrementScores(eq(CURRENT_HOUR_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue()).singleElement()
                    .satisfies(s -> assertThat(s.score()).isCloseTo(0.1, offset(0.001)));
        }
    }

    @DisplayName("시간 단위 상품을 랭킹에서 제거할 때,")
    @Nested
    class RemoveHourlyProducts {

        @DisplayName("현재 시간 키와 다음 시간 키에서 해당 상품을 제거한다.")
        @Test
        void removesProductFromCurrentAndNextHourKey() {
            given(eventHandledRepository.markIfAbsent("hourly:e1")).willReturn(true);
            rankingService.removeHourlyProducts(List.of(new RankingEvent.Delete("e1", 5L)));
            then(rankingRepository).should().removeMembers(eq(CURRENT_HOUR_KEY), eq(List.of(5L)));
            then(rankingRepository).should().removeMembers(eq(NEXT_HOUR_KEY), eq(List.of(5L)));
        }

        @DisplayName("빈 목록이면, Repository를 호출하지 않는다.")
        @Test
        void skips_whenEmpty() {
            rankingService.removeHourlyProducts(Collections.emptyList());
            then(rankingRepository).should(never()).removeMembers(anyString(), anyList());
        }
    }
}

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

import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.RankingEvent;
import com.loopers.domain.ranking.RankingScore;
import com.loopers.domain.ranking.RankingScoreCalculator;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    private static final String TODAY_KEY = "ranking:v1:all:"
            + LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
    private static final String TOMORROW_KEY = "ranking:v1:all:"
            + LocalDate.now().plusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);

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

    @DisplayName("배치를 처리할 때,")
    @Nested
    class ProcessBatch {

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
            rankingService.processBatch(events);

            // assert
            then(rankingRepository).should().incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            List<RankingScore> scores = scoresCaptor.getValue();
            assertThat(scores).extracting(RankingScore::productId, RankingScore::score)
                    .containsExactlyInAnyOrder(
                            tuple(1L, 0.2),
                            tuple(2L, 0.1)
                    );
        }

        @DisplayName("좋아요/취소 이벤트의 score를 합산한다.")
        @Test
        void aggregatesLikeScores() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(true);
            RankingEvent.Like like1 = new RankingEvent.Like("e1", 1L, true);
            RankingEvent.Like like2 = new RankingEvent.Like("e2", 1L, true);
            RankingEvent.Like unlike = new RankingEvent.Like("e3", 1L, false);
            given(scoreCalculator.calculate(like1)).willReturn(new RankingScore(1L, 0.2));
            given(scoreCalculator.calculate(like2)).willReturn(new RankingScore(1L, 0.2));
            given(scoreCalculator.calculate(unlike)).willReturn(new RankingScore(1L, -0.2));
            List<RankingEvent> events = List.of(like1, like2, unlike);

            // act
            rankingService.processBatch(events);

            // assert
            then(rankingRepository).should().incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue())
                    .singleElement()
                    .satisfies(s -> assertThat(s.score()).isCloseTo(0.2, offset(0.001)));
        }

        @DisplayName("주문 이벤트의 항목별 score를 productId별로 합산한다.")
        @Test
        void aggregatesOrderScores() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(true);
            RankingEvent.Order order = new RankingEvent.Order("e1", List.of(
                    new RankingEvent.Order.OrderItem(1L, 50000L, 1L),
                    new RankingEvent.Order.OrderItem(2L, 30000L, 2L)
            ));
            given(scoreCalculator.calculate(order)).willReturn(List.of(
                    new RankingScore(1L, 2.82),
                    new RankingScore(2L, 3.0)
            ));
            List<RankingEvent> events = List.of(order);

            // act
            rankingService.processBatch(events);

            // assert
            then(rankingRepository).should().incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue()).extracting(RankingScore::productId, RankingScore::score)
                    .containsExactlyInAnyOrder(
                            tuple(1L, 2.82),
                            tuple(2L, 3.0)
                    );
        }

        @DisplayName("혼합 이벤트를 단일 Pipeline으로 처리한다.")
        @Test
        void aggregatesMixedEvents() {
            // arrange
            given(eventHandledRepository.markIfAbsent(anyString())).willReturn(true);
            RankingEvent.View view = new RankingEvent.View("e1", 1L);
            RankingEvent.Like like = new RankingEvent.Like("e2", 1L, true);
            RankingEvent.Order order = new RankingEvent.Order("e3", List.of(
                    new RankingEvent.Order.OrderItem(1L, 50000L, 1L)
            ));
            given(scoreCalculator.calculate(view)).willReturn(new RankingScore(1L, 0.1));
            given(scoreCalculator.calculate(like)).willReturn(new RankingScore(1L, 0.2));
            given(scoreCalculator.calculate(order)).willReturn(List.of(new RankingScore(1L, 2.82)));
            List<RankingEvent> events = List.of(view, like, order);

            // act
            rankingService.processBatch(events);

            // assert
            then(rankingRepository).should(times(1)).incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue())
                    .singleElement()
                    .satisfies(s -> assertThat(s.score()).isCloseTo(3.12, offset(0.001)));
        }

        @DisplayName("빈 리스트이면, Repository를 호출하지 않는다.")
        @Test
        void skips_whenEmpty() {
            // act
            rankingService.processBatch(Collections.emptyList());

            // assert
            then(rankingRepository).should(times(0)).incrementScores(anyString(), anyList());
        }

        @DisplayName("중복 이벤트는 skip한다.")
        @Test
        void skipsDuplicateEvents() {
            // arrange
            given(eventHandledRepository.markIfAbsent("e1")).willReturn(true);
            given(eventHandledRepository.markIfAbsent("e2")).willReturn(false);
            RankingEvent.View view1 = new RankingEvent.View("e1", 1L);
            given(scoreCalculator.calculate(view1)).willReturn(new RankingScore(1L, 0.1));
            List<RankingEvent> events = List.of(
                    view1,
                    new RankingEvent.View("e2", 1L)
            );

            // act
            rankingService.processBatch(events);

            // assert — e2는 skip되어 score가 0.1만 반영
            then(rankingRepository).should().incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            assertThat(scoresCaptor.getValue())
                    .singleElement()
                    .satisfies(s -> {
                        assertThat(s.productId()).isEqualTo(1L);
                        assertThat(s.score()).isCloseTo(0.1, offset(0.001));
                    });
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
            List<RankingEvent> events = List.of(like, unlike, view);

            // act
            rankingService.processBatch(events);

            // assert — productId=1은 0.0이므로 제외, productId=2만 포함
            then(rankingRepository).should().incrementScores(eq(TODAY_KEY), scoresCaptor.capture());
            List<RankingScore> scores = scoresCaptor.getValue();
            assertThat(scores).extracting(RankingScore::productId).doesNotContain(1L);
            assertThat(scores).singleElement()
                    .satisfies(s -> {
                        assertThat(s.productId()).isEqualTo(2L);
                        assertThat(s.score()).isCloseTo(0.1, offset(0.001));
                    });
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
            then(rankingRepository).should(times(0)).incrementScores(anyString(), anyList());
        }

    }

    @DisplayName("상품을 랭킹에서 제거할 때,")
    @Nested
    class RemoveProducts {

        @DisplayName("멱등성 체크 후 오늘과 내일 키에서 해당 상품을 제거한다.")
        @Test
        void removesProductFromTodayAndTomorrowKey() {
            // arrange
            given(eventHandledRepository.markIfAbsent("e1")).willReturn(true);

            // act
            rankingService.removeProducts(List.of(new RankingEvent.Delete("e1", 5L)));

            // assert
            then(rankingRepository).should().removeMembers(eq(TODAY_KEY), eq(List.of(5L)));
            then(rankingRepository).should().removeMembers(eq(TOMORROW_KEY), eq(List.of(5L)));
        }

        @DisplayName("중복 이벤트는 skip하고, 빈 리스트로 ZREM을 호출한다.")
        @Test
        void removesNothing_whenAllDuplicate() {
            // arrange
            given(eventHandledRepository.markIfAbsent("e1")).willReturn(false);

            // act
            rankingService.removeProducts(List.of(new RankingEvent.Delete("e1", 5L)));

            // assert
            then(rankingRepository).should().removeMembers(eq(TODAY_KEY), eq(Collections.emptyList()));
            then(rankingRepository).should().removeMembers(eq(TOMORROW_KEY), eq(Collections.emptyList()));
        }

        @DisplayName("빈 목록이면, Repository를 호출하지 않는다.")
        @Test
        void skips_whenEmpty() {
            // act
            rankingService.removeProducts(Collections.emptyList());

            // assert
            then(rankingRepository).should(never()).removeMembers(anyString(), anyList());
        }
    }
}

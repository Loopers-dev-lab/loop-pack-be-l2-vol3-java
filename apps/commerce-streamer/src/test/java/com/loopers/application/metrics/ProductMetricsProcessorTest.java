package com.loopers.application.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.domain.ranking.RankingRepository;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductMetricsProcessorTest {

    @Mock
    private ProductMetricsRepository productMetricsRepository;

    @Mock
    private EventHandledRepository eventHandledRepository;

    @Mock
    private RankingRepository rankingRepository;

    @InjectMocks
    private ProductMetricsProcessor processor;

    @Captor
    private ArgumentCaptor<Double> scoreCaptor;

    private static final Long PRODUCT_ID = 1L;
    private static final ZonedDateTime NOW = ZonedDateTime.now();

    @BeforeEach
    void setUp() {
        lenient().when(eventHandledRepository.existsByEventIdAndEventType(any(), any())).thenReturn(false);
        lenient().when(eventHandledRepository.existsByEntityIdAndEventTypeAndOccurredAtGreaterThanEqual(any(), any(), any())).thenReturn(false);
        lenient().when(productMetricsRepository.findByProductId(PRODUCT_ID))
            .thenReturn(Optional.of(ProductMetrics.restore(1L, PRODUCT_ID, 0, 0, 0)));
    }

    @Nested
    @DisplayName("이벤트 타입별 가중치 적용")
    class EventWeightTest {

        @Test
        @DisplayName("PRODUCT_VIEWED 이벤트 발생 시, +0.1 점수로 ZSET을 업데이트한다")
        void product_viewed_increments_score_by_0_1() {
            processor.process("event-1", "PRODUCT_VIEWED", PRODUCT_ID, null, NOW);

            verify(rankingRepository).incrementScore(eq(PRODUCT_ID), scoreCaptor.capture(), eq(NOW.toLocalDate()));
            assertThat(scoreCaptor.getValue()).isCloseTo(0.1, within(0.001));
        }

        @Test
        @DisplayName("LIKED 이벤트 발생 시, +0.2 점수로 ZSET을 업데이트한다")
        void liked_increments_score_by_0_2() {
            processor.process("event-1", "LIKED", PRODUCT_ID, null, NOW);

            verify(rankingRepository).incrementScore(eq(PRODUCT_ID), scoreCaptor.capture(), eq(NOW.toLocalDate()));
            assertThat(scoreCaptor.getValue()).isCloseTo(0.2, within(0.001));
        }

        @Test
        @DisplayName("UNLIKED 이벤트 발생 시, -0.2 점수로 ZSET을 업데이트한다")
        void unliked_decrements_score_by_0_2() {
            processor.process("event-1", "UNLIKED", PRODUCT_ID, null, NOW);

            verify(rankingRepository).incrementScore(eq(PRODUCT_ID), scoreCaptor.capture(), eq(NOW.toLocalDate()));
            assertThat(scoreCaptor.getValue()).isCloseTo(-0.2, within(0.001));
        }

        @Test
        @DisplayName("ORDER_CONFIRMED 이벤트 발생 시, 0.7 * log1p(수량) 점수로 ZSET을 업데이트한다")
        void order_confirmed_increments_score_by_log_formula() {
            int quantity = 1;
            double expectedScore = 0.7 * Math.log1p(quantity);

            processor.process("event-1", "ORDER_CONFIRMED", PRODUCT_ID, quantity, NOW);

            verify(rankingRepository).incrementScore(eq(PRODUCT_ID), scoreCaptor.capture(), eq(NOW.toLocalDate()));
            assertThat(scoreCaptor.getValue()).isCloseTo(expectedScore, within(0.001));
        }

        @Test
        @DisplayName("ORDER_CONFIRMED(qty=4) 1건의 점수는 LIKED 3건 합산 점수보다 크다 - 주문 가중치 우선")
        void order_score_outweighs_three_likes() {
            // ORDER_CONFIRMED qty=4 : 0.7 * log1p(4) ≈ 1.127
            // LIKED × 3             : 0.2 × 3 = 0.6
            processor.process("event-order", "ORDER_CONFIRMED", PRODUCT_ID, 4, NOW);

            verify(rankingRepository).incrementScore(eq(PRODUCT_ID), scoreCaptor.capture(), eq(NOW.toLocalDate()));
            double orderScore = scoreCaptor.getValue();

            assertThat(orderScore).isGreaterThan(3 * 0.2);
        }

        @Test
        @DisplayName("수량이 증가할수록 ORDER_CONFIRMED 점수도 증가한다")
        void order_score_increases_with_quantity() {
            processor.process("event-1", "ORDER_CONFIRMED", PRODUCT_ID, 1, NOW);
            processor.process("event-2", "ORDER_CONFIRMED", PRODUCT_ID, 10, NOW);

            verify(rankingRepository, times(2)).incrementScore(eq(PRODUCT_ID), scoreCaptor.capture(), any());

            List<Double> scores = scoreCaptor.getAllValues();
            assertThat(scores.get(1)).isGreaterThan(scores.get(0));
        }
    }
}

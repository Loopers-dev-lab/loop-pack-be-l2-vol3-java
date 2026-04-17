package com.loopers.application.metrics;

import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.ranking.RankingMetricsService;
import com.loopers.domain.ranking.RankingMetricsSummary;
import com.loopers.interfaces.consumer.OutboxMessage;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class OrderEventHandlerIntegrationTest {

    private static final Long PRODUCT_ID_A = 100L;
    private static final Long PRODUCT_ID_B = 200L;
    private static final Long ORDER_ID = 1L;

    @Autowired
    private OrderEventHandler orderEventHandler;

    @Autowired
    private RankingMetricsService rankingMetricsService;

    @Autowired
    private EventHandledRepository eventHandledRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private OutboxMessage orderCreatedWithItems(Long eventId) {
        String payload = """
                {
                  "orderId": %d,
                  "userId": 1,
                  "totalAmount": 59800,
                  "items": [
                    {"productId": %d, "productName": "상품A", "price": 29900, "quantity": 1},
                    {"productId": %d, "productName": "상품B", "price": 14950, "quantity": 2}
                  ]
                }
                """.formatted(ORDER_ID, PRODUCT_ID_A, PRODUCT_ID_B);
        return new OutboxMessage(eventId, "ORDER", ORDER_ID, "ORDER_CREATED", payload);
    }

    private OutboxMessage orderCreatedWithoutItems(Long eventId) {
        String payload = """
                {
                  "orderId": %d,
                  "userId": 1,
                  "totalAmount": 29900,
                  "itemCount": 1
                }
                """.formatted(ORDER_ID);
        return new OutboxMessage(eventId, "ORDER", ORDER_ID, "ORDER_CREATED", payload);
    }

    @DisplayName("ORDER_CREATED 이벤트 처리 — items 포함")
    @Nested
    class HandleOrderCreatedWithItems {

        @DisplayName("items 배열의 각 상품별 order_revenue가 ranking_metrics에 반영된다")
        @Test
        void orderRevenueReflected() {
            // act
            orderEventHandler.handle(orderCreatedWithItems(1L));

            // assert
            RankingMetricsSummary summaryA = rankingMetricsService.sumByProductIdAndDate(PRODUCT_ID_A, LocalDate.now());
            assertThat(summaryA.totalOrderRevenue()).isEqualByComparingTo(BigDecimal.valueOf(29900));

            RankingMetricsSummary summaryB = rankingMetricsService.sumByProductIdAndDate(PRODUCT_ID_B, LocalDate.now());
            // 14950 × 2 = 29900
            assertThat(summaryB.totalOrderRevenue()).isEqualByComparingTo(BigDecimal.valueOf(29900));
        }

        @DisplayName("동일 이벤트를 두 번 처리해도 한 번만 반영된다 (멱등성)")
        @Test
        void duplicateEventIgnored() {
            // arrange
            OutboxMessage message = orderCreatedWithItems(1L);

            // act
            orderEventHandler.handle(message);
            orderEventHandler.handle(message);

            // assert
            RankingMetricsSummary summaryA = rankingMetricsService.sumByProductIdAndDate(PRODUCT_ID_A, LocalDate.now());
            assertThat(summaryA.totalOrderRevenue()).isEqualByComparingTo(BigDecimal.valueOf(29900));
        }
    }

    @DisplayName("ORDER_CREATED 이벤트 처리 — items 미포함 (하위 호환)")
    @Nested
    class HandleOrderCreatedWithoutItems {

        @DisplayName("items가 없는 기존 메시지는 ranking_metrics에 영향 없이 정상 처리된다")
        @Test
        void noRankingMetricsCreated() {
            // act
            orderEventHandler.handle(orderCreatedWithoutItems(1L));

            // assert — ranking_metrics에 데이터 없음
            RankingMetricsSummary summary = rankingMetricsService.sumByProductIdAndDate(PRODUCT_ID_A, LocalDate.now());
            assertThat(summary).isNull();

            // 이벤트 처리 기록은 있음
            assertThat(eventHandledRepository.existsByEventId(1L)).isTrue();
        }
    }
}

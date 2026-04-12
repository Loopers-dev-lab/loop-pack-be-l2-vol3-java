package com.loopers.application.ranking;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BatchAggregator 단위 테스트")
class BatchAggregatorTest {

    private BatchAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new BatchAggregator(new ObjectMapper());
    }

    private ConsumerRecord<String, String> record(String topic, String value) {
        return new ConsumerRecord<>(topic, 0, 0L, "key", value);
    }

    @Nested
    @DisplayName("catalog-events 집계")
    class Catalog {

        @Test
        @DisplayName("PRODUCT_VIEWED 이벤트는 view +1 로 집계된다")
        void viewed() {
            // given
            String json = """
                    {"eventId":"e1","eventType":"PRODUCT_VIEWED","data":{"productId":100}}
                    """;
            // when
            Map<Long, MetricDelta> result =
                    aggregator.aggregateCatalog(List.of(record("catalog-events", json)));
            // then
            assertThat(result).containsOnlyKeys(100L);
            assertThat(result.get(100L).view()).isEqualTo(1);
            assertThat(result.get(100L).eventIds()).containsExactly("e1");
        }

        @Test
        @DisplayName("같은 상품의 view 이벤트 3건은 view 3 으로 합산")
        void multipleViewsAggregated() {
            // given
            List<ConsumerRecord<String, String>> records = List.of(
                    record("catalog-events", """
                            {"eventId":"v1","eventType":"PRODUCT_VIEWED","data":{"productId":1}}
                            """),
                    record("catalog-events", """
                            {"eventId":"v2","eventType":"PRODUCT_VIEWED","data":{"productId":1}}
                            """),
                    record("catalog-events", """
                            {"eventId":"v3","eventType":"PRODUCT_VIEWED","data":{"productId":1}}
                            """)
            );
            // when
            Map<Long, MetricDelta> result = aggregator.aggregateCatalog(records);
            // then
            assertThat(result.get(1L).view()).isEqualTo(3);
            assertThat(result.get(1L).eventIds()).containsExactly("v1", "v2", "v3");
        }

        @Test
        @DisplayName("PRODUCT_LIKED liked=true 는 +1, liked=false 는 -1 로 상쇄")
        void likeAndUnlikeCancelOut() {
            // given
            List<ConsumerRecord<String, String>> records = List.of(
                    record("catalog-events", """
                            {"eventId":"l1","eventType":"PRODUCT_LIKED","data":{"productId":2,"liked":true}}
                            """),
                    record("catalog-events", """
                            {"eventId":"l2","eventType":"PRODUCT_LIKED","data":{"productId":2,"liked":false}}
                            """)
            );
            // when
            Map<Long, MetricDelta> result = aggregator.aggregateCatalog(records);
            // then
            assertThat(result.get(2L).like()).isEqualTo(0);
        }

        @Test
        @DisplayName("서로 다른 상품은 분리되어 집계")
        void separateProducts() {
            // given
            List<ConsumerRecord<String, String>> records = List.of(
                    record("catalog-events", """
                            {"eventId":"v1","eventType":"PRODUCT_VIEWED","data":{"productId":1}}
                            """),
                    record("catalog-events", """
                            {"eventId":"v2","eventType":"PRODUCT_VIEWED","data":{"productId":2}}
                            """)
            );
            // when
            Map<Long, MetricDelta> result = aggregator.aggregateCatalog(records);
            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(1L).view()).isEqualTo(1);
            assertThat(result.get(2L).view()).isEqualTo(1);
        }

        @Test
        @DisplayName("잘못된 JSON 레코드는 skip 하고 나머지는 정상 처리")
        void brokenJsonSkipped() {
            // given
            List<ConsumerRecord<String, String>> records = List.of(
                    record("catalog-events", "{broken"),
                    record("catalog-events", """
                            {"eventId":"ok","eventType":"PRODUCT_VIEWED","data":{"productId":1}}
                            """)
            );
            // when
            Map<Long, MetricDelta> result = aggregator.aggregateCatalog(records);
            // then
            assertThat(result).containsOnlyKeys(1L);
            assertThat(result.get(1L).view()).isEqualTo(1);
        }

        @Test
        @DisplayName("빈 리스트는 빈 Map 을 반환한다")
        void empty() {
            assertThat(aggregator.aggregateCatalog(List.of())).isEmpty();
            assertThat(aggregator.aggregateCatalog(null)).isEmpty();
        }

        @Test
        @DisplayName("productId 가 문자열 'abc' 이면 0 으로 집계되지 않고 skip 된다")
        void productIdStringSkipped() {
            // given
            String json = """
                    {"eventId":"e1","eventType":"PRODUCT_VIEWED","data":{"productId":"abc"}}
                    """;
            // when
            Map<Long, MetricDelta> result = aggregator.aggregateCatalog(List.of(record("catalog-events", json)));
            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("productId 가 빈 문자열이면 skip 된다")
        void productIdEmptyStringSkipped() {
            // given
            String json = """
                    {"eventId":"e1","eventType":"PRODUCT_VIEWED","data":{"productId":""}}
                    """;
            // when
            Map<Long, MetricDelta> result = aggregator.aggregateCatalog(List.of(record("catalog-events", json)));
            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("productId 가 boolean true 이면 1 로 집계되지 않고 skip 된다")
        void productIdBooleanSkipped() {
            // given
            String json = """
                    {"eventId":"e1","eventType":"PRODUCT_VIEWED","data":{"productId":true}}
                    """;
            // when
            Map<Long, MetricDelta> result = aggregator.aggregateCatalog(List.of(record("catalog-events", json)));
            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("order-events 집계")
    class Order {

        @Test
        @DisplayName("ORDER_PAID 의 orderedProducts 가 상품별 order/amount 로 집계된다")
        void orderAggregation() {
            // given
            String json = """
                    {
                      "eventId":"o1",
                      "eventType":"ORDER_PAID",
                      "data":{
                        "orderedProducts":[
                          {"productId":1,"quantity":2,"unitPrice":10000},
                          {"productId":2,"quantity":1,"unitPrice":50000}
                        ]
                      }
                    }
                    """;
            // when
            Map<Long, MetricDelta> result =
                    aggregator.aggregateOrder(List.of(record("order-events", json)));
            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(1L).order()).isEqualTo(2);
            assertThat(result.get(1L).amount()).isEqualByComparingTo(BigDecimal.valueOf(20000));
            assertThat(result.get(2L).order()).isEqualTo(1);
            assertThat(result.get(2L).amount()).isEqualByComparingTo(BigDecimal.valueOf(50000));
        }

        @Test
        @DisplayName("unitPrice 누락 시 금액은 0 으로 집계, 건수는 정상")
        void missingUnitPrice() {
            // given
            String json = """
                    {"eventId":"o1","eventType":"ORDER_PAID","data":{"orderedProducts":[
                      {"productId":1,"quantity":2}
                    ]}}
                    """;
            // when
            Map<Long, MetricDelta> result =
                    aggregator.aggregateOrder(List.of(record("order-events", json)));
            // then
            assertThat(result.get(1L).order()).isEqualTo(2);
            assertThat(result.get(1L).amount()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("quantity 가 0 이하인 라인은 skip")
        void quantityZeroSkipped() {
            // given
            String json = """
                    {"eventId":"o1","eventType":"ORDER_PAID","data":{"orderedProducts":[
                      {"productId":1,"quantity":0,"unitPrice":10000}
                    ]}}
                    """;
            // when
            Map<Long, MetricDelta> result =
                    aggregator.aggregateOrder(List.of(record("order-events", json)));
            // then
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("알 수 없는 eventType 은 skip")
        void unknownType() {
            // given
            String json = """
                    {"eventId":"o1","eventType":"UNKNOWN","data":{"orderedProducts":[]}}
                    """;
            // when
            Map<Long, MetricDelta> result =
                    aggregator.aggregateOrder(List.of(record("order-events", json)));
            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("extractEventId")
    class ExtractEventId {
        @Test
        @DisplayName("정상 payload 에서 eventId 를 꺼낸다")
        void extractsEventId() {
            // given
            ConsumerRecord<String, String> rec = record("catalog-events", """
                    {"eventId":"abc","eventType":"PRODUCT_VIEWED","data":{"productId":1}}
                    """);
            // expect
            assertThat(aggregator.extractEventId(rec)).isEqualTo("abc");
        }

        @Test
        @DisplayName("파싱 실패 시 null")
        void brokenReturnsNull() {
            ConsumerRecord<String, String> rec = record("catalog-events", "{not-json");
            assertThat(aggregator.extractEventId(rec)).isNull();
        }
    }
}

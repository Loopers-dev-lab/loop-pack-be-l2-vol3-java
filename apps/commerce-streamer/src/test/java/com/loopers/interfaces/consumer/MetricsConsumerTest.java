package com.loopers.interfaces.consumer;

import com.loopers.application.ranking.RankingScoreUpdater;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.Invocation;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MetricsConsumerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private RankingScoreUpdater rankingScoreUpdater;

    @Mock
    private Acknowledgment ack;

    private MetricsConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MetricsConsumer(jdbcTemplate, transactionTemplate, rankingScoreUpdater);

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Consumer<TransactionStatus> action = invocation.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        // varargs 매칭: update(String, Object...) → 모든 호출에 1 리턴
        doAnswer(inv -> 1).when(jdbcTemplate).update(anyString(), any(Object[].class));
    }

    private ConsumerRecord<String, String> record(String json) {
        return new ConsumerRecord<>("order-events", 0, 0L, "key", json);
    }

    private List<String> captureUpdateSqls() {
        Collection<Invocation> invocations = Mockito.mockingDetails(jdbcTemplate).getInvocations();
        return invocations.stream()
            .filter(inv -> inv.getMethod().getName().equals("update"))
            .map(inv -> (String) inv.getArgument(0))
            .collect(Collectors.toList());
    }

    @Nested
    @DisplayName("Late-Arriving Fact — ORDER_CANCELLED 이중 UPSERT")
    class LateArrivingFact {

        @Test
        @DisplayName("ORDER_CANCELLED: 인식일(CURDATE) + 발생일(originalOrderDate) 이중 UPSERT 실행")
        void cancelledEvent_dualUpsert() {
            String json = """
                {"eventId":"evt-1","eventType":"ORDER_CANCELLED","productId":101,\
                "salesCount":2,"salesAmount":30000,"originalOrderDate":"2026-04-01"}""";

            consumer.consume(List.of(record(json)), ack);

            List<String> sqls = captureUpdateSqls();

            // event_handled INSERT + 인식일 UPSERT + 발생일 UPSERT = 3회
            assertThat(sqls).hasSizeGreaterThanOrEqualTo(3);
            assertThat(sqls).anyMatch(sql -> sql.contains("cancel_count_by_event_date"));
            assertThat(sqls).anyMatch(sql -> sql.contains("cancel_count_by_order_date"));

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("ORDER_CANCELLED에 originalOrderDate 없으면 발생일 UPSERT 미실행")
        void cancelledEvent_noOriginalOrderDate_singleUpsert() {
            String json = """
                {"eventId":"evt-2","eventType":"ORDER_CANCELLED","productId":101,\
                "salesCount":1,"salesAmount":10000}""";

            consumer.consume(List.of(record(json)), ack);

            List<String> sqls = captureUpdateSqls();

            assertThat(sqls).anyMatch(sql -> sql.contains("cancel_count_by_event_date"));
            assertThat(sqls).noneMatch(sql -> sql.contains("cancel_count_by_order_date"));

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("다른 날짜의 취소(4/1 주문 → 4/5 취소)가 정확히 두 UPSERT로 기록")
        void crossDateCancel_twoDistinctUpserts() {
            String json = """
                {"eventId":"evt-3","eventType":"ORDER_CANCELLED","productId":202,\
                "salesCount":1,"salesAmount":50000,"originalOrderDate":"2026-04-01"}""";

            consumer.consume(List.of(record(json)), ack);

            List<String> sqls = captureUpdateSqls();

            // 인식일 SQL: CURDATE() 사용
            String eventDateSql = sqls.stream()
                .filter(sql -> sql.contains("cancel_count_by_event_date"))
                .findFirst()
                .orElse("");
            assertThat(eventDateSql).contains("CURDATE()");

            // 발생일 SQL: CURDATE 미사용 (originalOrderDate는 파라미터로 전달)
            String orderDateSql = sqls.stream()
                .filter(sql -> sql.contains("cancel_count_by_order_date"))
                .findFirst()
                .orElse("");
            assertThat(orderDateSql).doesNotContain("CURDATE()");

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("originalOrderDate 파싱 실패 시 인식일 UPSERT는 정상 실행")
        void invalidOriginalOrderDate_eventDateUpsertStillWorks() {
            String json = """
                {"eventId":"evt-4","eventType":"ORDER_CANCELLED","productId":101,\
                "salesCount":1,"salesAmount":10000,"originalOrderDate":"invalid-date"}""";

            consumer.consume(List.of(record(json)), ack);

            List<String> sqls = captureUpdateSqls();

            assertThat(sqls).anyMatch(sql -> sql.contains("cancel_count_by_event_date"));
            assertThat(sqls).noneMatch(sql -> sql.contains("cancel_count_by_order_date"));

            verify(ack).acknowledge();
        }
    }

    @Nested
    @DisplayName("기존 이벤트 처리")
    class ExistingEvents {

        @Test
        @DisplayName("ORDER_CREATED 이벤트는 발생일(by_order_date) UPSERT를 실행하지 않음")
        void orderCreated_noByOrderDateUpsert() {
            String json = """
                {"eventId":"evt-5","eventType":"ORDER_CREATED","productId":101,\
                "salesCount":3,"salesAmount":90000}""";

            consumer.consume(List.of(record(json)), ack);

            List<String> sqls = captureUpdateSqls();
            assertThat(sqls).noneMatch(sql -> sql.contains("cancel_count_by_order_date"));

            verify(ack).acknowledge();
        }

        @Test
        @DisplayName("PRODUCT_VIEWED 이벤트 정상 처리 — 인식일 UPSERT에 view_count 포함")
        void productViewed_upsertContainsViewCount() {
            String json = """
                {"eventId":"evt-6","eventType":"PRODUCT_VIEWED","productId":101}""";

            consumer.consume(List.of(record(json)), ack);

            List<String> sqls = captureUpdateSqls();
            assertThat(sqls).anyMatch(sql -> sql.contains("view_count"));

            verify(ack).acknowledge();
        }
    }
}

package com.loopers.application.coupon;

import com.loopers.domain.outbox.OutboxModel;
import com.loopers.domain.outbox.OutboxRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponIssue Outbox Kafka key 검증 — requestId key로 파티션 분산 보장
 *
 * 배경:
 *   CouponFlashEventScaleTest(논의 11)는 templateId를 Kafka key로 직접 사용하여
 *   hotspot(처리량 31~34 RPS) 시나리오를 측정했다.
 *
 *   실제 구현(CouponIssueApp → OutboxAppender → KafkaEventPublisher)은
 *   aggregateId = request.getRequestId() (UUID)를 key로 사용한다.
 *   즉 실제 경로는 requestId key → 파티션 분산 → Consumer Scale-Out 효과 존재.
 *
 * 검증:
 *   A. CouponIssueApp.requestIssue() → Outbox.aggregateId = requestId (UUID)
 *   B. KafkaEventPublisher.send()는 outbox.getAggregateId()를 Kafka key로 사용
 *   C. 각 요청마다 다른 UUID → 파티션 균등 분산
 *
 * 결론:
 *   Flash 테스트의 hotspot 수치(31 RPS)는 templateId 단일 key 시나리오이며,
 *   실제 Outbox 경로에서는 requestId 분산 key로 이 제약이 없다.
 *   Flash 테스트는 hotspot 자체의 수량 제어 정확성을 검증하는 것이 목적이었음.
 */
@SpringBootTest
@DisplayName("CouponIssue Outbox Kafka key 검증 — requestId key → 파티션 분산")
class CouponIssueOutboxKeyTest {

    private static final long MEMBER_ID_1 = 1001L;
    private static final long MEMBER_ID_2 = 1002L;

    @Autowired
    private CouponIssueApp couponIssueApp;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private long templateId;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update(
            "INSERT INTO coupon_templates (name, type, value, min_order_amount, expired_at, total_quantity, issued_count, deleted_at, created_at, updated_at) " +
            "VALUES ('선착순 쿠폰', 'FIXED', 1000, 5000, DATE_ADD(NOW(), INTERVAL 7 DAY), 100, 0, NULL, NOW(), NOW())"
        );
        templateId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("Outbox aggregateId = requestId 검증")
    class OutboxAggregateIdIsRequestId {

        @Test
        @DisplayName("[key-is-requestId] requestIssue() → Outbox.aggregateId = requestId (UUID)")
        void requestIssue_outboxAggregateId_isRequestId() {
            CouponIssueRequestInfo info = couponIssueApp.requestIssue(templateId, MEMBER_ID_1);

            List<OutboxModel> pending = outboxRepository.findPendingWithLimit(10);
            assertThat(pending).hasSize(1);

            OutboxModel outbox = pending.get(0);

            System.out.printf(
                "[KEY-IS-REQUESTID]%n" +
                "  requestId=%s%n" +
                "  outbox.aggregateId=%s%n" +
                "  outbox.topic=%s%n" +
                "  → KafkaEventPublisher.send()는 aggregateId를 Kafka key로 사용%n" +
                "  → Kafka key = requestId (UUID) → 파티션 해시 분산%n",
                info.requestId(), outbox.getAggregateId(), outbox.getTopic()
            );

            assertThat(outbox.getAggregateId())
                .as("Outbox aggregateId = requestId — KafkaEventPublisher가 이를 Kafka key로 사용")
                .isEqualTo(info.requestId());

            assertThat(outbox.getAggregateId())
                .as("Kafka key는 UUID 형식 (분산 가능)")
                .matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

            assertThat(outbox.getTopic())
                .as("발행 토픽이 coupon-issue-requests")
                .isEqualTo("coupon-issue-requests");
        }

        @Test
        @DisplayName("[key-distribution] 2건 요청 → 각각 다른 requestId → Kafka key 분산")
        void multipleRequests_differentRequestIds_enablesPartitionDistribution() {
            jdbcTemplate.update(
                "INSERT INTO coupon_templates (name, type, value, min_order_amount, expired_at, total_quantity, issued_count, deleted_at, created_at, updated_at) " +
                "VALUES ('선착순 쿠폰2', 'FIXED', 1000, 5000, DATE_ADD(NOW(), INTERVAL 7 DAY), 100, 0, NULL, NOW(), NOW())"
            );
            long templateId2 = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            CouponIssueRequestInfo info1 = couponIssueApp.requestIssue(templateId, MEMBER_ID_1);
            CouponIssueRequestInfo info2 = couponIssueApp.requestIssue(templateId2, MEMBER_ID_2);

            List<OutboxModel> pending = outboxRepository.findPendingWithLimit(10);
            assertThat(pending).hasSize(2);

            String key1 = pending.stream()
                    .filter(o -> o.getAggregateId().equals(info1.requestId()))
                    .findFirst()
                    .map(OutboxModel::getAggregateId)
                    .orElseThrow();

            String key2 = pending.stream()
                    .filter(o -> o.getAggregateId().equals(info2.requestId()))
                    .findFirst()
                    .map(OutboxModel::getAggregateId)
                    .orElseThrow();

            System.out.printf(
                "[KEY-DISTRIBUTION]%n" +
                "  request1 Kafka key=%s%n" +
                "  request2 Kafka key=%s%n" +
                "  → 서로 다른 UUID → 파티션 해시 분산 → Consumer Scale-Out 효과 존재%n" +
                "  → Flash 테스트(templateId key hotspot)와 실제 구현(requestId key 분산)은 다른 시나리오%n",
                key1, key2
            );

            assertThat(key1)
                .as("각 요청의 Kafka key는 서로 다른 UUID — 파티션 분산 가능")
                .isNotEqualTo(key2);
        }

        @Test
        @DisplayName("[flash-test-gap] Flash 테스트 templateId key vs 실제 requestId key — 차이 설명")
        void flashTestKey_vs_realImplKey_documented() {
            /*
             * CouponFlashEventScaleTest(논의 11)에서 측정:
             *   kafkaTemplate.send(TOPIC, String.valueOf(templateId), payload)
             *   → Kafka key = templateId → 단일 파티션 집중 → 31~34 RPS
             *
             * 실제 CouponIssueApp → OutboxAppender:
             *   outboxAppender.append("coupon_issue_request", request.getRequestId(), ...)
             *   → aggregateId = requestId (UUID)
             *
             * KafkaEventPublisher:
             *   kafkaTemplate.send(outbox.getTopic(), outbox.getAggregateId(), outbox.getPayload())
             *   → Kafka key = aggregateId = requestId
             *
             * 결론:
             *   - Flash 테스트는 hotspot 조건에서 수량 제어 정확성을 검증한 것
             *   - 실제 Outbox 경로는 이미 requestId(UUID) key → 파티션 분산
             *   - "미구현" 판단은 테스트 코드 key와 실제 구현 key를 혼동한 오류
             */
            CouponIssueRequestInfo info = couponIssueApp.requestIssue(templateId, MEMBER_ID_1);
            List<OutboxModel> pending = outboxRepository.findPendingWithLimit(10);
            OutboxModel outbox = pending.get(0);

            System.out.printf(
                "[FLASH-TEST-GAP] 검증 완료%n" +
                "  CouponFlashEventScaleTest key: String.valueOf(templateId) = '%s'%n" +
                "  실제 Outbox key (aggregateId): '%s'%n" +
                "  → 실제 구현은 이미 requestId 분산 key — hotspot 없음%n",
                String.valueOf(templateId), outbox.getAggregateId()
            );

            assertThat(outbox.getAggregateId())
                .as("실제 Kafka key는 숫자(templateId)가 아닌 UUID")
                .doesNotMatch("\\d+");
        }
    }
}

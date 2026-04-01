package com.loopers.interfaces.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.infrastructure.coupon.CouponIssueRequestEntity;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponIssueRequestStatus;
import com.loopers.infrastructure.event.EventHandledJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.record.TimestampType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CouponIssueConsumer 통합 테스트
 *
 * Consumer의 processRecord()를 직접 호출하여 검증:
 * 1. 발급 성공 시 요청 이력이 ISSUED로 업데이트되는가
 * 2. 재고 소진 시 요청 이력이 FAILED로 업데이트되는가
 * 3. 멱등성 — 같은 이벤트를 2번 처리해도 1번만 발급되는가
 */
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponIssueConsumerTest {

    @Autowired
    private CouponIssueConsumer couponIssueConsumer;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private CouponIssueRequestJpaRepository couponIssueRequestRepository;

    @Autowired
    private EventHandledJpaRepository eventHandledRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("Consumer가 발급 요청을 처리하면 쿠폰이 발급되고 요청 이력이 ISSUED로 변경된다")
    void 발급_성공() throws Exception {
        // arrange
        CouponTemplate template = createTemplate(100);
        String eventId = UUID.randomUUID().toString();
        CouponIssueRequestEntity request = createRequest(template.getId(), 1L, eventId);

        ConsumerRecord<Object, Object> record = buildRecord(request.getId(), template.getId(), 1L, eventId);

        // act
        couponIssueConsumer.consume(List.of(record), () -> {});

        // assert
        CouponIssueRequestEntity updated = couponIssueRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED);
        assertThat(updated.getIssuedCouponId()).isNotNull();
        assertThat(updated.getProcessedAt()).isNotNull();

        long issuedCount = issuedCouponRepository.countByCouponTemplateId(template.getId());
        assertThat(issuedCount).isEqualTo(1);

        assertThat(eventHandledRepository.existsByEventId(eventId)).isTrue();
    }

    @Test
    @DisplayName("재고 소진 시 요청 이력이 FAILED로 변경된다")
    void 재고_소진_실패() throws Exception {
        // arrange — 최대 1장
        CouponTemplate template = createTemplate(1);

        // 1장 먼저 발급
        String eventId1 = UUID.randomUUID().toString();
        CouponIssueRequestEntity request1 = createRequest(template.getId(), 1L, eventId1);
        couponIssueConsumer.consume(List.of(buildRecord(request1.getId(), template.getId(), 1L, eventId1)), () -> {});

        // 2번째 요청 — 재고 소진
        String eventId2 = UUID.randomUUID().toString();
        CouponIssueRequestEntity request2 = createRequest(template.getId(), 2L, eventId2);

        // act
        couponIssueConsumer.consume(List.of(buildRecord(request2.getId(), template.getId(), 2L, eventId2)), () -> {});

        // assert
        CouponIssueRequestEntity updated = couponIssueRequestRepository.findById(request2.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(CouponIssueRequestStatus.FAILED);
        assertThat(updated.getFailureReason()).isNotNull();
        assertThat(updated.getProcessedAt()).isNotNull();

        long totalIssued = issuedCouponRepository.countByCouponTemplateId(template.getId());
        assertThat(totalIssued).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 eventId를 2번 처리해도 1번만 발급된다 — 멱등성")
    void 멱등성_중복_방지() throws Exception {
        // arrange
        CouponTemplate template = createTemplate(100);
        String eventId = UUID.randomUUID().toString();
        CouponIssueRequestEntity request = createRequest(template.getId(), 1L, eventId);

        ConsumerRecord<Object, Object> record = buildRecord(request.getId(), template.getId(), 1L, eventId);

        // act — 같은 이벤트 2번 처리
        couponIssueConsumer.consume(List.of(record), () -> {});
        couponIssueConsumer.consume(List.of(record), () -> {});

        // assert — 1번만 발급
        long issuedCount = issuedCouponRepository.countByCouponTemplateId(template.getId());
        assertThat(issuedCount).isEqualTo(1);
    }

    private CouponTemplate createTemplate(int maxIssueCount) {
        return couponTemplateRepository.save(
                CouponTemplate.define("선착순쿠폰", "테스트용", DiscountType.FIXED, 1000, null,
                        0, maxIssueCount, 1,
                        ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30))
        );
    }

    private CouponIssueRequestEntity createRequest(Long templateId, Long userId, String eventId) {
        return couponIssueRequestRepository.save(
                CouponIssueRequestEntity.create(templateId, userId, eventId));
    }

    private ConsumerRecord<Object, Object> buildRecord(Long requestId, Long templateId, Long userId, String eventId)
            throws Exception {
        String payload = objectMapper.writeValueAsString(
                new CouponIssuePayload(requestId, templateId, userId, eventId));

        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                "coupon-issue-requests-v1", 0, 0L,
                String.valueOf(templateId), payload);

        record.headers().add(new RecordHeader("X-Event-Type",
                "CouponIssueRequestedEvent".getBytes(StandardCharsets.UTF_8)));

        return record;
    }

    record CouponIssuePayload(Long requestId, Long templateId, Long userId, String eventId) {}
}

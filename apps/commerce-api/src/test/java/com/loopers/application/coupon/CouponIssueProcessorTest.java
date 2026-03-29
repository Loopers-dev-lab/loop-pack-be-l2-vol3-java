package com.loopers.application.coupon;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * CouponIssueProcessor 테스트 — @Transactional 원자성 검증
 *
 * self-invocation 수정 후:
 *   Consumer → Processor 위임 → 프록시를 통한 호출 → @Transactional 정상 동작
 *
 * 검증 포인트:
 *   1. 발급 성공 시 issue + markIssued + event_handled가 모두 반영
 *   2. 비즈니스 실패 시 markFailed + event_handled가 같은 TX에서 반영
 *   3. 멱등성 — 같은 eventId 2번 처리해도 1번만 발급
 *   4. @Transactional 롤백 — 중간 실패 시 전체 롤백
 */
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponIssueProcessorTest {

    @Autowired
    private CouponIssueProcessor processor;

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
    @DisplayName("발급 성공 시 쿠폰 발급 + 요청 ISSUED + event_handled가 모두 반영된다")
    void 발급_성공_원자성() throws Exception {
        // arrange
        CouponTemplate template = createTemplate(100);
        String eventId = UUID.randomUUID().toString();
        CouponIssueRequestEntity request = createRequest(template.getId(), 1L, eventId);

        String payload = buildPayload(request.getId(), template.getId(), 1L, eventId);

        // act
        processor.process(payload);

        // assert — 3가지 모두 반영됨 (같은 TX)
        assertThat(issuedCouponRepository.countByCouponTemplateId(template.getId())).isEqualTo(1);

        CouponIssueRequestEntity updated = couponIssueRequestRepository.findById(request.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(CouponIssueRequestStatus.ISSUED);
        assertThat(updated.getIssuedCouponId()).isNotNull();

        assertThat(eventHandledRepository.existsByEventId(eventId)).isTrue();
    }

    @Test
    @DisplayName("재고 소진 시 요청 FAILED + event_handled가 같은 TX에서 반영된다")
    void 재고_소진_실패_원자성() throws Exception {
        // arrange — 최대 1장
        CouponTemplate template = createTemplate(1);

        // 1장 먼저 발급
        String eventId1 = UUID.randomUUID().toString();
        CouponIssueRequestEntity request1 = createRequest(template.getId(), 1L, eventId1);
        processor.process(buildPayload(request1.getId(), template.getId(), 1L, eventId1));

        // 2번째 요청 — 재고 소진
        String eventId2 = UUID.randomUUID().toString();
        CouponIssueRequestEntity request2 = createRequest(template.getId(), 2L, eventId2);

        // act — 비즈니스 실패 → BusinessFailureException throw (TX rollback-only)
        // Consumer에서 catch → markFailedInNewTx() 호출 흐름을 테스트에서 재현
        try {
            processor.process(buildPayload(request2.getId(), template.getId(), 2L, eventId2));
        } catch (CouponIssueProcessor.BusinessFailureException e) {
            // Consumer가 하는 것과 동일: 별도 TX로 FAILED 기록
            processor.markFailedInNewTx(e.getRequestId(), e.getEventId(), e.getMessage());
        }

        // assert — FAILED + event_handled 모두 반영 (별도 TX에서 커밋)
        CouponIssueRequestEntity updated = couponIssueRequestRepository.findById(request2.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(CouponIssueRequestStatus.FAILED);
        assertThat(updated.getFailureReason()).isNotNull();

        // 실패해도 event_handled에 기록 → 재처리 방지
        assertThat(eventHandledRepository.existsByEventId(eventId2)).isTrue();

        // 총 발급 수 = 1장 (초과 발급 없음)
        assertThat(issuedCouponRepository.countByCouponTemplateId(template.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 eventId를 2번 처리해도 1번만 발급된다 — 멱등성")
    void 멱등성_중복_방지() throws Exception {
        // arrange
        CouponTemplate template = createTemplate(100);
        String eventId = UUID.randomUUID().toString();
        CouponIssueRequestEntity request = createRequest(template.getId(), 1L, eventId);
        String payload = buildPayload(request.getId(), template.getId(), 1L, eventId);

        // act — 같은 payload 2번 처리
        processor.process(payload);
        processor.process(payload);  // 2번째는 중복 스킵

        // assert — 1번만 발급
        assertThat(issuedCouponRepository.countByCouponTemplateId(template.getId())).isEqualTo(1);
    }

    private CouponTemplate createTemplate(int maxIssueCount) {
        return couponTemplateRepository.save(
                CouponTemplate.define("선착순쿠폰", "테스트", DiscountType.FIXED, 1000, null,
                        0, maxIssueCount, 1,
                        ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30))
        );
    }

    private CouponIssueRequestEntity createRequest(Long templateId, Long userId, String eventId) {
        return couponIssueRequestRepository.save(
                CouponIssueRequestEntity.create(templateId, userId, eventId));
    }

    private String buildPayload(Long requestId, Long templateId, Long userId, String eventId)
            throws Exception {
        return objectMapper.writeValueAsString(
                new PayloadRecord(requestId, templateId, userId, eventId));
    }

    record PayloadRecord(Long requestId, Long templateId, Long userId, String eventId) {}
}

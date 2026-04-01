package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.infrastructure.coupon.CouponIssueRequestEntity;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponIssueRequestStatus;
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository;
import com.loopers.infrastructure.outbox.OutboxStatus;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 선착순 쿠폰 비동기 발급 요청 테스트
 *
 * 검증 대상:
 * 1. requestCouponIssue() 호출 시 요청 이력(PENDING) + Outbox가 같은 TX에 저장되는가
 * 2. 폴링으로 발급 결과를 조회할 수 있는가
 * 3. Outbox에 올바른 topic, partitionKey가 저장되는가
 */
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CouponFacadeAsyncIssueTest {

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private CouponIssueRequestJpaRepository couponIssueRequestRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("비동기 발급 요청 시 요청 이력(PENDING)과 Outbox가 같은 TX에 저장된다")
    void 비동기_발급_요청_원자성() {
        // arrange
        CouponTemplate template = createTemplate(100);

        // act
        CouponFacade.CouponIssueRequestResult result = couponFacade.requestCouponIssue(template.getId(), 1L);

        // assert — 요청 이력 확인
        assertThat(result.status()).isEqualTo("PENDING");
        assertThat(result.requestId()).isNotNull();
        assertThat(result.eventId()).isNotNull();

        CouponIssueRequestEntity request = couponIssueRequestRepository.findById(result.requestId()).orElseThrow();
        assertThat(request.getStatus()).isEqualTo(CouponIssueRequestStatus.PENDING);
        assertThat(request.getCouponTemplateId()).isEqualTo(template.getId());
        assertThat(request.getUserId()).isEqualTo(1L);

        // assert — Outbox 확인
        var pendingOutbox = outboxEventRepository.findPendingEvents(50);
        assertThat(pendingOutbox).hasSize(1);
        assertThat(pendingOutbox.get(0).getTopic()).isEqualTo("coupon-issue-requests-v1");
        assertThat(pendingOutbox.get(0).getPartitionKey()).isEqualTo(String.valueOf(template.getId()));
        assertThat(pendingOutbox.get(0).getEventType()).isEqualTo("CouponIssueRequestedEvent");
        assertThat(pendingOutbox.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    @DisplayName("폴링으로 발급 결과를 조회할 수 있다 — 초기 상태는 PENDING")
    void 폴링_결과_조회() {
        // arrange
        CouponTemplate template = createTemplate(100);
        CouponFacade.CouponIssueRequestResult requested = couponFacade.requestCouponIssue(template.getId(), 1L);

        // act
        CouponFacade.CouponIssueRequestResult polled = couponFacade.getCouponIssueResult(requested.requestId());

        // assert
        assertThat(polled.status()).isEqualTo("PENDING");
        assertThat(polled.requestId()).isEqualTo(requested.requestId());
    }

    @Test
    @DisplayName("여러 유저의 발급 요청이 각각 별도 Outbox 이벤트로 저장된다")
    void 다건_발급_요청() {
        // arrange
        CouponTemplate template = createTemplate(100);

        // act
        couponFacade.requestCouponIssue(template.getId(), 1L);
        couponFacade.requestCouponIssue(template.getId(), 2L);
        couponFacade.requestCouponIssue(template.getId(), 3L);

        // assert
        assertThat(couponIssueRequestRepository.count()).isEqualTo(3);
        assertThat(outboxEventRepository.findPendingEvents(50)).hasSize(3);

        // 모든 Outbox의 partitionKey가 같은 templateId → 같은 Kafka 파티션
        var outboxEvents = outboxEventRepository.findPendingEvents(50);
        assertThat(outboxEvents).allMatch(e ->
                e.getPartitionKey().equals(String.valueOf(template.getId())));
    }

    private CouponTemplate createTemplate(int maxIssueCount) {
        return couponTemplateRepository.save(
                CouponTemplate.define("선착순쿠폰", "테스트용", DiscountType.FIXED, 1000, null,
                        0, maxIssueCount, 1,
                        ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30))
        );
    }
}

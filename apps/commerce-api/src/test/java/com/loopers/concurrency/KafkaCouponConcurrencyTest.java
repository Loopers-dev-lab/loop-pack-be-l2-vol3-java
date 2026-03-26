package com.loopers.concurrency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.CouponTemplateRepository;
import com.loopers.domain.coupon.DiscountType;
import com.loopers.domain.coupon.IssuedCouponRepository;
import com.loopers.infrastructure.coupon.CouponIssueRequestEntity;
import com.loopers.infrastructure.coupon.CouponIssueRequestJpaRepository;
import com.loopers.infrastructure.coupon.CouponIssueRequestStatus;
import com.loopers.infrastructure.event.EventHandledJpaRepository;
import com.loopers.infrastructure.outbox.OutboxEventJpaRepository;
import com.loopers.interfaces.consumer.CouponIssueConsumer;
import com.loopers.utils.DatabaseCleanUp;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kafka 기반 선착순 쿠폰 발급 동시성 테스트
 *
 * 검증 대상:
 * 1. N명이 동시에 발급 요청 → Outbox에 N건 저장 (Producer 동시성)
 * 2. Consumer가 순차 처리 → 정확히 maxIssueCount만 발급 (Consumer 동시성)
 * 3. 초과 발급 0건
 * 4. 발급 성공 수 + 실패 수 = 요청 수
 *
 * Kafka를 거치지 않고 Consumer를 직접 호출하여 테스트:
 *   → Kafka 파티션 순차 처리를 시뮬레이션
 *   → 실제 Kafka E2E는 별도 통합 테스트에서 검증
 */
@SpringBootTest
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class KafkaCouponConcurrencyTest {

    @Autowired
    private CouponFacade couponFacade;

    @Autowired
    private CouponIssueConsumer couponIssueConsumer;

    @Autowired
    private CouponTemplateRepository couponTemplateRepository;

    @Autowired
    private IssuedCouponRepository issuedCouponRepository;

    @Autowired
    private CouponIssueRequestJpaRepository couponIssueRequestRepository;

    @Autowired
    private OutboxEventJpaRepository outboxEventRepository;

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
    @DisplayName("100명이 동시에 선착순 쿠폰(10장)을 요청하면, 정확히 10명만 발급된다")
    void 선착순_쿠폰_동시_발급_요청_100명_10장() throws InterruptedException {
        // arrange — 최대 10장 발급 가능한 쿠폰
        CouponTemplate template = createTemplate(10);
        int userCount = 100;

        // act 1: 100명이 동시에 발급 요청 (Producer 동시성)
        List<CouponFacade.CouponIssueRequestResult> requestResults =
                concurrentIssueRequests(template.getId(), userCount);

        // assert 1: 100건 모두 PENDING으로 생성됨
        assertThat(requestResults).hasSize(userCount);
        assertThat(couponIssueRequestRepository.count()).isEqualTo(userCount);
        assertThat(outboxEventRepository.count()).isEqualTo(userCount);

        // act 2: Consumer가 순차 처리 (Kafka 파티션 순차 처리 시뮬레이션)
        processAllRequestsSequentially(requestResults);

        // assert 2: 정확히 10명만 발급
        long issuedCount = issuedCouponRepository.countByCouponTemplateId(template.getId());
        assertThat(issuedCount).isEqualTo(10);

        // assert 3: 발급 성공 10건 + 실패 90건 = 100건
        long successCount = couponIssueRequestRepository.findAll().stream()
                .filter(r -> r.getStatus() == CouponIssueRequestStatus.ISSUED)
                .count();
        long failCount = couponIssueRequestRepository.findAll().stream()
                .filter(r -> r.getStatus() == CouponIssueRequestStatus.FAILED)
                .count();

        assertThat(successCount).isEqualTo(10);
        assertThat(failCount).isEqualTo(90);
        assertThat(successCount + failCount).isEqualTo(userCount);

        // assert 4: 멱등성 기록도 100건
        assertThat(eventHandledRepository.count()).isEqualTo(userCount);
    }

    @Test
    @DisplayName("200명이 동시에 선착순 쿠폰(50장)을 요청하면, 정확히 50명만 발급된다")
    void 선착순_쿠폰_동시_발급_요청_200명_50장() throws InterruptedException {
        // arrange
        CouponTemplate template = createTemplate(50);
        int userCount = 200;

        // act
        List<CouponFacade.CouponIssueRequestResult> requestResults =
                concurrentIssueRequests(template.getId(), userCount);
        processAllRequestsSequentially(requestResults);

        // assert
        long issuedCount = issuedCouponRepository.countByCouponTemplateId(template.getId());
        assertThat(issuedCount).isEqualTo(50);

        long successCount = couponIssueRequestRepository.findAll().stream()
                .filter(r -> r.getStatus() == CouponIssueRequestStatus.ISSUED)
                .count();
        long failCount = couponIssueRequestRepository.findAll().stream()
                .filter(r -> r.getStatus() == CouponIssueRequestStatus.FAILED)
                .count();

        assertThat(successCount).isEqualTo(50);
        assertThat(failCount).isEqualTo(150);
    }

    @Test
    @DisplayName("같은 유저가 같은 쿠폰을 2번 요청해도 1번만 발급된다 — Consumer 멱등성")
    void 동일_유저_중복_요청_멱등성() throws Exception {
        // arrange
        CouponTemplate template = createTemplate(100);

        // 같은 유저가 2번 요청
        CouponFacade.CouponIssueRequestResult request1 = couponFacade.requestCouponIssue(template.getId(), 1L);
        CouponFacade.CouponIssueRequestResult request2 = couponFacade.requestCouponIssue(template.getId(), 1L);

        // act — Consumer 순차 처리
        processRequest(request1);
        processRequest(request2);

        // assert — CouponService.issue()의 유저별 발급 수 체크로 2번째 실패
        // 첫 번째: ISSUED, 두 번째: FAILED (maxIssueCountPerUser=1 초과)
        long issuedCount = issuedCouponRepository.countByCouponTemplateId(template.getId());
        assertThat(issuedCount).isEqualTo(1);
    }

    // ──────────────────────────────────────────────

    private CouponTemplate createTemplate(int maxIssueCount) {
        return couponTemplateRepository.save(
                CouponTemplate.define("선착순쿠폰", "동시성테스트용", DiscountType.FIXED, 1000, null,
                        0, maxIssueCount, 1,
                        ZonedDateTime.now().minusDays(1), ZonedDateTime.now().plusDays(30))
        );
    }

    /**
     * N명이 동시에 requestCouponIssue()를 호출
     * startLatch로 모든 스레드가 동시에 출발하도록 보장
     */
    private List<CouponFacade.CouponIssueRequestResult> concurrentIssueRequests(Long templateId, int userCount)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(userCount, 50));
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(userCount);
        List<CouponFacade.CouponIssueRequestResult> results = new ArrayList<>();
        AtomicInteger errorCount = new AtomicInteger(0);

        for (int i = 0; i < userCount; i++) {
            long userId = i + 1L;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    CouponFacade.CouponIssueRequestResult result =
                            couponFacade.requestCouponIssue(templateId, userId);
                    synchronized (results) {
                        results.add(result);
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();  // 모든 스레드 동시 출발
        doneLatch.await();
        executor.shutdown();

        assertThat(errorCount.get()).isZero();  // 요청 자체는 전부 성공해야 함
        return results;
    }

    /**
     * 모든 발급 요청을 Consumer가 순차 처리 (Kafka 파티션 순차 처리 시뮬레이션)
     */
    private void processAllRequestsSequentially(List<CouponFacade.CouponIssueRequestResult> requests)
            throws InterruptedException {
        for (CouponFacade.CouponIssueRequestResult request : requests) {
            processRequest(request);
        }
    }

    private void processRequest(CouponFacade.CouponIssueRequestResult request) throws InterruptedException {
        CouponIssueRequestEntity entity = couponIssueRequestRepository.findById(request.requestId()).orElseThrow();

        try {
            String payload = objectMapper.writeValueAsString(
                    new CouponPayload(entity.getId(), entity.getCouponTemplateId(),
                            entity.getUserId(), entity.getEventId()));

            ConsumerRecord<Object, Object> record = new ConsumerRecord<>(
                    "coupon-issue-requests-v1", 0, 0L,
                    String.valueOf(entity.getCouponTemplateId()), payload);
            record.headers().add(new RecordHeader("X-Event-Type",
                    "CouponIssueRequestedEvent".getBytes(StandardCharsets.UTF_8)));

            couponIssueConsumer.consume(List.of(record), () -> {});

        } catch (Exception e) {
            // Consumer 내부에서 처리되므로 여기까지 오면 안 됨
            throw new RuntimeException("Consumer 호출 실패", e);
        }
    }

    record CouponPayload(Long requestId, Long templateId, Long userId, String eventId) {}
}

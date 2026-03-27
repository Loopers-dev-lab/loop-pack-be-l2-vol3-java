package com.loopers.application.coupon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.coupon.CouponIssueResult;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.UserCoupon;
import com.loopers.domain.event.EventHandled;
import com.loopers.domain.event.EventHandledRepository;
import com.loopers.infrastructure.coupon.CouponIssueResultJpaRepository;
import com.loopers.infrastructure.coupon.CouponTemplateJpaRepository;
import com.loopers.infrastructure.coupon.UserCouponJpaRepository;
import com.loopers.interfaces.consumer.OutboxMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 선착순 쿠폰 발급 요청 처리 핸들러.
 *
 * Kafka 파티션 키가 templateId이므로 같은 쿠폰 템플릿은 순차 처리된다.
 * → 동시성 제어 코드(@Version, 비관적 락) 불필요.
 *
 * 처리 흐름:
 * 1. 멱등성 체크 (event_handled)
 * 2. 만료 체크
 * 3. 수량 체크 (canIssue)
 * 4. 중복 발급 체크 (existsByTemplateAndUser)
 * 5. 발급 (user_coupons INSERT + coupon_issue_results 상태 갱신)
 *
 * 비즈니스 거절 시 Redis INCR로 재고 복구 (API에서 이미 DECR 차감했으므로).
 */
@Slf4j
@Component
public class CouponIssueHandler {

    private static final String STOCK_KEY_PREFIX = "coupon:stock:";

    private final CouponTemplateJpaRepository couponTemplateJpaRepository;
    private final CouponIssueResultJpaRepository couponIssueResultJpaRepository;
    private final UserCouponJpaRepository userCouponJpaRepository;
    private final EventHandledRepository eventHandledRepository;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;

    public CouponIssueHandler(
            CouponTemplateJpaRepository couponTemplateJpaRepository,
            CouponIssueResultJpaRepository couponIssueResultJpaRepository,
            UserCouponJpaRepository userCouponJpaRepository,
            EventHandledRepository eventHandledRepository,
            ObjectMapper objectMapper,
            @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate) {
        this.couponTemplateJpaRepository = couponTemplateJpaRepository;
        this.couponIssueResultJpaRepository = couponIssueResultJpaRepository;
        this.userCouponJpaRepository = userCouponJpaRepository;
        this.eventHandledRepository = eventHandledRepository;
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public void handle(OutboxMessage message) {
        // 1. 멱등성 체크
        if (eventHandledRepository.existsByEventId(message.eventId())) {
            log.debug("[CouponIssueHandler] 이미 처리된 이벤트 skip: eventId={}", message.eventId());
            return;
        }

        JsonNode payload = parsePayload(message.payload());
        Long couponIssueResultId = payload.get("couponIssueResultId").asLong();
        Long couponTemplateId = payload.get("couponTemplateId").asLong();
        Long userId = payload.get("userId").asLong();

        CouponIssueResult issueResult = couponIssueResultJpaRepository.findById(couponIssueResultId)
                .orElseThrow(() -> new IllegalStateException(
                        "CouponIssueResult not found: id=" + couponIssueResultId));

        CouponTemplate template = couponTemplateJpaRepository.findById(couponTemplateId)
                .orElseThrow(() -> new IllegalStateException(
                        "CouponTemplate not found: id=" + couponTemplateId));

        // 2. 만료 체크
        if (template.isExpired(LocalDateTime.now())) {
            rejectAndRestoreStock(issueResult, "만료된 쿠폰 템플릿입니다.", couponTemplateId, message);
            log.info("[CouponIssueHandler] 만료 거절: templateId={}, userId={}", couponTemplateId, userId);
            return;
        }

        // 3. 수량 체크
        if (!template.canIssue()) {
            issueResult.markSoldOut();
            restoreRedisStock(couponTemplateId);
            saveHandled(message);
            log.info("[CouponIssueHandler] 매진: templateId={}, userId={}", couponTemplateId, userId);
            return;
        }

        // 4. 중복 발급 체크 (최종 보장 — API best-effort를 통과한 동시 요청 방어)
        if (userCouponJpaRepository.existsByCouponTemplateIdAndUserIdAndDeletedAtIsNull(
                couponTemplateId, userId)) {
            rejectAndRestoreStock(issueResult, "이미 발급된 쿠폰입니다.", couponTemplateId, message);
            log.info("[CouponIssueHandler] 중복 거절: templateId={}, userId={}", couponTemplateId, userId);
            return;
        }

        // 5. 발급 성공
        template.incrementIssuedCount();
        userCouponJpaRepository.save(new UserCoupon(couponTemplateId, userId, template.getExpiredAt()));
        issueResult.markIssued();

        saveHandled(message);
        log.info("[CouponIssueHandler] 발급 성공: templateId={}, userId={}, issuedCount={}",
                couponTemplateId, userId, template.getCurrentIssuedCount());
    }

    // 비즈니스 거절 시 issueResult 상태 갱신 + Redis 재고 복구 + 이벤트 처리 기록
    private void rejectAndRestoreStock(CouponIssueResult issueResult, String reason,
                                        Long couponTemplateId, OutboxMessage message) {
        issueResult.markRejected(reason);
        restoreRedisStock(couponTemplateId);
        saveHandled(message);
    }

    // Redis 재고 복구 (INCR). API에서 DECR한 분을 되돌린다.
    private void restoreRedisStock(Long couponTemplateId) {
        try {
            redisTemplate.opsForValue().increment(STOCK_KEY_PREFIX + couponTemplateId);
        } catch (Exception e) {
            // Redis 복구 실패는 보정 스케줄러가 추후 동기화하므로 예외를 삼키되 로그 남김
            log.warn("[CouponIssueHandler] Redis 재고 복구 실패 (보정 스케줄러에서 동기화 예정): templateId={}",
                    couponTemplateId, e);
        }
    }

    private void saveHandled(OutboxMessage message) {
        eventHandledRepository.save(new EventHandled(message.eventId(), message.eventType()));
    }

    private JsonNode parsePayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("payload 파싱 실패: " + payload, e);
        }
    }
}

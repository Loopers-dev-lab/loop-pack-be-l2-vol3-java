package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponIssueRequest;
import com.loopers.domain.coupon.CouponIssueRequestRepository;
import com.loopers.domain.coupon.CouponIssueDomainService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponDomainService;
import com.loopers.domain.coupon.FcfsCoupon;
import com.loopers.domain.coupon.FcfsCouponRepository;
import com.loopers.domain.event.CouponIssueRequestPayload;
import com.loopers.domain.eventhandled.EventHandled;
import com.loopers.domain.eventhandled.EventHandledRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@RequiredArgsConstructor
@Service
public class FcfsCouponIssueService {

    private final FcfsCouponRepository fcfsCouponRepository;
    private final CouponIssueRequestRepository couponIssueRequestRepository;
    private final CouponIssueDomainService couponIssueDomainService;
    private final CouponDomainService couponDomainService;
    private final EventHandledRepository eventHandledRepository;
    private final StringRedisTemplate redisTemplate;
    private final PlatformTransactionManager transactionManager;

    private static final String FCFS_COUNTER_KEY_PREFIX = "coupon:fcfs:";

    /**
     * Redis INCR은 트랜잭션 밖에서 gate 역할. DB 작업만 TX로 감싼다.
     * DB 커밋 실패 시 Redis DECR 보상으로 일관성 유지.
     */
    public void processIssueRequest(String eventId, CouponIssueRequestPayload payload) {
        FcfsCoupon fcfsCoupon = fcfsCouponRepository.findByCouponId(payload.couponId())
            .orElse(null);
        if (fcfsCoupon == null) {
            updateRequestFailed(payload.requestId(), "선착순 쿠폰 정보를 찾을 수 없습니다.");
            return;
        }

        int maxQuantity = fcfsCoupon.getMaxQuantity();
        String counterKey = FCFS_COUNTER_KEY_PREFIX + payload.couponId() + ":count";
        Long currentCount = redisTemplate.opsForValue().increment(counterKey);

        if (currentCount == null || currentCount > maxQuantity) {
            if (currentCount != null) {
                try {
                    redisTemplate.opsForValue().decrement(counterKey);
                } catch (Exception e) {
                    log.error("[Redis DECR 보상 실패] 수동 보정 필요. couponId={}, counterKey={}",
                        payload.couponId(), counterKey, e);
                }
            }
            updateRequestFailed(payload.requestId(), "선착순 마감");
            log.info("[선착순 마감] couponId={}, userId={}, count={}",
                payload.couponId(), payload.userId(), currentCount);
            return;
        }

        try {
            TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
            txTemplate.executeWithoutResult(status -> {
                Coupon coupon = couponDomainService.getById(payload.couponId());
                couponIssueDomainService.issue(coupon, payload.userId());

                FcfsCoupon fcfs = fcfsCouponRepository.findByCouponId(payload.couponId())
                    .orElseThrow(() -> new IllegalStateException("FcfsCoupon not found: " + payload.couponId()));
                fcfs.incrementIssuedCount();
                fcfsCouponRepository.save(fcfs);

                CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(payload.requestId())
                    .orElseThrow(() -> new IllegalStateException("발급 요청을 찾을 수 없습니다: " + payload.requestId()));
                request.markSuccess();
                couponIssueRequestRepository.save(request);

                eventHandledRepository.save(new EventHandled(eventId));
            });
            log.info("[쿠폰 발급 성공] couponId={}, userId={}, count={}/{}",
                payload.couponId(), payload.userId(), currentCount, maxQuantity);
        } catch (Exception e) {
            try {
                redisTemplate.opsForValue().decrement(counterKey);
            } catch (Exception redisEx) {
                log.error("[Redis DECR 보상 실패] 수동 보정 필요. couponId={}, counterKey={}",
                    payload.couponId(), counterKey, redisEx);
            }
            updateRequestFailed(payload.requestId(), e.getMessage());
            log.warn("[쿠폰 발급 실패] couponId={}, userId={}, error={}",
                payload.couponId(), payload.userId(), e.getMessage());
            throw e;
        }
    }

    private void updateRequestFailed(String requestId, String reason) {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.executeWithoutResult(status -> {
            CouponIssueRequest request = couponIssueRequestRepository.findByRequestId(requestId)
                .orElse(null);
            if (request != null) {
                request.markFailed(reason);
                couponIssueRequestRepository.save(request);
            }
        });
    }
}

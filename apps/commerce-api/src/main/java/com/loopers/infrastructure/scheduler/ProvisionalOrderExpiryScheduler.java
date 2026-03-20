package com.loopers.infrastructure.scheduler;

import com.loopers.infrastructure.redis.ProvisionalOrderRedisRepository;
import com.loopers.infrastructure.redis.StockReservationRedisRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 가주문 선제 만료 배치 — Proactive Expiry Scanner.
 *
 * <p>TTL < 30초인 가주문을 선제적으로 정리: 재고 복원(INCR) + 가주문 삭제(DEL).
 * 결제 미진행 가주문이 TTL 만료되면 Key만 삭제되고 재고는 미복원되는 문제(G7) 해결.</p>
 *
 * <p>30초 주기, 비용: SMEMBERS ~5건 + TTL 확인 ~5건 = ~2ms/회, 부하율 0.007%</p>
 *
 * @see <a href="06-resilience-review.md §16.14.3">Proactive Expiry Scanner</a>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvisionalOrderExpiryScheduler {

    private static final long EXPIRY_THRESHOLD_SECONDS = 30;

    private final ProvisionalOrderRedisRepository provisionalOrderRedisRepository;
    private final StockReservationRedisRepository stockReservationRedisRepository;

    @Scheduled(fixedRate = 30_000)
    public void cleanupExpiringOrders() {
        Set<Long> orderIds = provisionalOrderRedisRepository.getAllOrderIds();
        if (orderIds.isEmpty()) return;

        int cleanedCount = 0;

        for (Long orderId : orderIds) {
            long ttl = provisionalOrderRedisRepository.getTtlSeconds(orderId);

            if (ttl == -2) {
                // 키가 이미 만료됨 → 다음 사이클에서 자연 정리
                continue;
            }

            if (ttl >= 0 && ttl < EXPIRY_THRESHOLD_SECONDS) {
                cleanupProvisionalOrder(orderId);
                cleanedCount++;
            }
        }

        if (cleanedCount > 0) {
            log.info("가주문 선제 정리 완료: {}건", cleanedCount);
        }
    }

    private void cleanupProvisionalOrder(Long orderId) {
        // 1. 가주문 데이터에서 상품/수량 정보 조회
        provisionalOrderRedisRepository.findByOrderId(orderId).ifPresent(orderData -> {
            restoreStock(orderId, orderData);
        });

        // 2. 가주문 삭제
        provisionalOrderRedisRepository.deleteByOrderId(orderId);
        log.info("가주문 선제 정리: orderId={}, TTL 만료 임박", orderId);
    }

    @SuppressWarnings("unchecked")
    private void restoreStock(Long orderId, Map<String, Object> orderData) {
        Object itemsObj = orderData.get("items");
        if (itemsObj instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> itemMap) {
                    Long productId = toLong(itemMap.get("productId"));
                    Integer quantity = toInt(itemMap.get("quantity"));
                    if (productId != null && quantity != null) {
                        stockReservationRedisRepository.increase(productId, quantity);
                        log.debug("재고 복원: orderId={}, productId={}, quantity=+{}",
                            orderId, productId, quantity);
                    }
                }
            }
        }
    }

    private Long toLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        return null;
    }

    private int toInt(Object value) {
        if (value instanceof Integer i) return i;
        if (value instanceof Long l) return l.intValue();
        if (value instanceof String s) return Integer.parseInt(s);
        return 0;
    }
}

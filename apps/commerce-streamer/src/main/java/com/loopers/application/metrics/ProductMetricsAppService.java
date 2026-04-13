package com.loopers.application.metrics;

import com.loopers.domain.event.EventHandledRepository;
import com.loopers.domain.event.EventHandledRepository.EventHandledRecord;
import com.loopers.domain.metrics.ProductDailyMetricsRepository;
import com.loopers.domain.metrics.ProductDailyMetricsRepository.DailyDelta;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.domain.metrics.ProductMetricsRepository.AllTimeDelta;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductMetricsAppService {
    private final ProductMetricsRepository productMetricsRepository;
    private final ProductDailyMetricsRepository productDailyMetricsRepository;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void handleLikeToggled(String eventId, Long productId, boolean liked, ZonedDateTime occurredAt) {
        if (eventHandledRepository.insertIgnore(eventId, occurredAt) == 0) {
            log.info("이미 처리된 이벤트: eventId={}", eventId);
            return;
        }

        ensureExists(productId);
        int affected = liked
                ? productMetricsRepository.incrementLikeCount(productId, occurredAt)
                : productMetricsRepository.decrementLikeCount(productId, occurredAt);

        if (affected == 0) {
            log.info("오래된 이벤트 무시: eventId={}, productId={}", eventId, productId);
        } else {
            int delta = liked ? 1 : -1;
            productDailyMetricsRepository.upsertLikeCount(productId, occurredAt.toLocalDate(), delta, occurredAt);
            log.info("좋아요 메트릭 갱신: productId={}, liked={}", productId, liked);
        }
    }

    @Transactional
    public void handleProductViewed(String eventId, Long productId, ZonedDateTime occurredAt) {
        if (eventHandledRepository.insertIgnore(eventId, occurredAt) == 0) {
            log.info("이미 처리된 이벤트: eventId={}", eventId);
            return;
        }

        ensureExists(productId);
        int affected = productMetricsRepository.incrementViewCount(productId, occurredAt);
        if (affected == 0) {
            log.info("오래된 이벤트 무시: eventId={}, productId={}", eventId, productId);
        } else {
            productDailyMetricsRepository.upsertViewCount(productId, occurredAt.toLocalDate(), occurredAt);
            log.info("조회 메트릭 갱신: productId={}", productId);
        }
    }

    @Transactional
    public void handleOrderCreated(String eventId, List<Long> productIds, long totalAmount, ZonedDateTime occurredAt) {
        if (eventHandledRepository.insertIgnore(eventId, occurredAt) == 0) {
            log.info("이미 처리된 이벤트: eventId={}", eventId);
            return;
        }

        long amountPerProduct = productIds.isEmpty() ? 0 : totalAmount / productIds.size();
        for (Long productId : productIds) {
            ensureExists(productId);
            productMetricsRepository.incrementSalesCount(productId, occurredAt);
            productDailyMetricsRepository.upsertOrderAmount(
                    productId, occurredAt.toLocalDate(), Math.max(amountPerProduct, 1), occurredAt);
        }
        log.info("판매 메트릭 갱신: productIds={}", productIds);
    }

    @Transactional
    public void handleOrderCanceled(String eventId, List<Long> productIds, long totalAmount, ZonedDateTime occurredAt) {
        if (eventHandledRepository.insertIgnore(eventId, occurredAt) == 0) {
            log.info("이미 처리된 이벤트: eventId={}", eventId);
            return;
        }

        long amountPerProduct = productIds.isEmpty() ? 0 : totalAmount / productIds.size();
        for (Long productId : productIds) {
            ensureExists(productId);
            productMetricsRepository.decrementSalesCount(productId, occurredAt);
            productDailyMetricsRepository.upsertOrderAmount(
                    productId, occurredAt.toLocalDate(), -Math.max(amountPerProduct, 1), occurredAt);
        }
        log.info("판매 메트릭 차감: productIds={}", productIds);
    }

    private void ensureExists(Long productId) {
        productMetricsRepository.insertIgnore(productId);
    }

    // ============================================================
    // 배치 처리 경로 (Kafka batch listener 용)
    // ============================================================

    @Transactional
    public void handleCatalogEventBatch(List<CatalogMetricEvent> events) {
        if (events == null || events.isEmpty()) return;

        // 1. 배치 내 중복 eventId 제거 (같은 배치에서 같은 오프셋이 두 번 올 일은 거의 없지만 방어)
        Map<String, CatalogMetricEvent> dedupedMap = new HashMap<>();
        for (CatalogMetricEvent e : events) {
            dedupedMap.putIfAbsent(e.eventId(), e);
        }
        List<CatalogMetricEvent> deduped = new ArrayList<>(dedupedMap.values());

        // 2. 이미 처리된 eventId 조회 → 필터
        List<String> eventIds = deduped.stream().map(CatalogMetricEvent::eventId).toList();
        Set<String> existing = eventHandledRepository.findExistingEventIds(eventIds);
        List<CatalogMetricEvent> fresh = deduped.stream()
                .filter(e -> !existing.contains(e.eventId()))
                .toList();

        if (fresh.isEmpty()) {
            log.info("catalog 배치 전체가 이미 처리됨: size={}", events.size());
            return;
        }

        // 3. event_handled 벌크 insertIgnore (안전망 — 병렬 컨슈머 대비)
        List<EventHandledRecord> handled = fresh.stream()
                .map(e -> new EventHandledRecord(e.eventId(), e.occurredAt()))
                .toList();
        eventHandledRepository.bulkInsertIgnore(handled);

        // 4. 집계 — productId 기준
        Map<Long, CatalogAggregation> agg = new HashMap<>();
        for (CatalogMetricEvent e : fresh) {
            CatalogAggregation cur = agg.get(e.productId());
            if (cur == null) {
                cur = new CatalogAggregation(0, 0, e.occurredAt());
                agg.put(e.productId(), cur);
            }
            long newView = cur.viewDelta;
            long newLike = cur.likeDelta;
            if (e.type() == CatalogMetricEvent.Type.VIEWED) {
                newView += 1;
            } else {
                newLike += e.liked() ? 1 : -1;
            }
            ZonedDateTime newest = e.occurredAt().isAfter(cur.lastOccurredAt) ? e.occurredAt() : cur.lastOccurredAt;
            agg.put(e.productId(), new CatalogAggregation(newView, newLike, newest));
        }

        // 5. product_metrics row 보장 + 벌크 all-time upsert
        Set<Long> productIds = agg.keySet();
        productMetricsRepository.bulkInsertIgnoreProducts(productIds);

        List<AllTimeDelta> allTimeDeltas = new ArrayList<>(agg.size());
        List<DailyDelta> dailyDeltas = new ArrayList<>(agg.size());
        for (Map.Entry<Long, CatalogAggregation> en : agg.entrySet()) {
            CatalogAggregation a = en.getValue();
            allTimeDeltas.add(new AllTimeDelta(en.getKey(), a.viewDelta, a.likeDelta, 0, a.lastOccurredAt));
            dailyDeltas.add(new DailyDelta(en.getKey(), a.lastOccurredAt.toLocalDate(),
                    a.viewDelta, a.likeDelta, 0, a.lastOccurredAt));
        }
        productMetricsRepository.bulkUpsertAllTime(allTimeDeltas);
        productDailyMetricsRepository.bulkUpsert(dailyDeltas);

        log.info("catalog 배치 처리 완료: total={}, fresh={}, products={}",
                events.size(), fresh.size(), productIds.size());
    }

    @Transactional
    public void handleOrderEventBatch(List<OrderMetricEvent> events) {
        if (events == null || events.isEmpty()) return;

        Map<String, OrderMetricEvent> dedupedMap = new HashMap<>();
        for (OrderMetricEvent e : events) {
            dedupedMap.putIfAbsent(e.eventId(), e);
        }
        List<OrderMetricEvent> deduped = new ArrayList<>(dedupedMap.values());

        List<String> eventIds = deduped.stream().map(OrderMetricEvent::eventId).toList();
        Set<String> existing = eventHandledRepository.findExistingEventIds(eventIds);
        List<OrderMetricEvent> fresh = deduped.stream()
                .filter(e -> !existing.contains(e.eventId()))
                .toList();

        if (fresh.isEmpty()) {
            log.info("order 배치 전체가 이미 처리됨: size={}", events.size());
            return;
        }

        List<EventHandledRecord> handled = fresh.stream()
                .map(e -> new EventHandledRecord(e.eventId(), e.occurredAt()))
                .toList();
        eventHandledRepository.bulkInsertIgnore(handled);

        // 집계: productId 기준. 같은 상품에 대한 create/cancel 이 섞일 수 있음.
        Map<Long, OrderAggregation> agg = new HashMap<>();
        for (OrderMetricEvent e : fresh) {
            if (e.productIds() == null || e.productIds().isEmpty()) continue;
            long perProduct = Math.max(e.totalAmount() / e.productIds().size(), 1);
            long sign = (e.type() == OrderMetricEvent.Type.CREATED) ? 1 : -1;
            long amountDelta = sign * perProduct;
            long salesDelta = sign;

            for (Long pid : e.productIds()) {
                OrderAggregation cur = agg.get(pid);
                if (cur == null) {
                    cur = new OrderAggregation(0, 0, e.occurredAt());
                    agg.put(pid, cur);
                }
                ZonedDateTime newest = e.occurredAt().isAfter(cur.lastOccurredAt) ? e.occurredAt() : cur.lastOccurredAt;
                agg.put(pid, new OrderAggregation(cur.salesDelta + salesDelta, cur.amountDelta + amountDelta, newest));
            }
        }

        if (agg.isEmpty()) {
            log.info("order 배치 처리: 집계 대상 없음 (productIds 비어있는 이벤트만)");
            return;
        }

        Set<Long> productIds = agg.keySet();
        productMetricsRepository.bulkInsertIgnoreProducts(productIds);

        List<AllTimeDelta> allTimeDeltas = new ArrayList<>(agg.size());
        List<DailyDelta> dailyDeltas = new ArrayList<>(agg.size());
        // metric_date 는 각 이벤트의 occurredAt.toLocalDate() 기준이어야 하므로
        // 같은 상품이 서로 다른 날짜 이벤트를 가지면 쪼개서 집계해야 하나,
        // 현실적으로 단일 배치는 수 초 범위이고 자정 경계 케이스는 무시 가능.
        // 단일 대표 날짜는 lastOccurredAt 기준 사용.
        for (Map.Entry<Long, OrderAggregation> en : agg.entrySet()) {
            OrderAggregation a = en.getValue();
            allTimeDeltas.add(new AllTimeDelta(en.getKey(), 0, 0, a.salesDelta, a.lastOccurredAt));
            dailyDeltas.add(new DailyDelta(en.getKey(), a.lastOccurredAt.toLocalDate(),
                    0, 0, a.amountDelta, a.lastOccurredAt));
        }
        productMetricsRepository.bulkUpsertAllTime(allTimeDeltas);
        productDailyMetricsRepository.bulkUpsert(dailyDeltas);

        log.info("order 배치 처리 완료: total={}, fresh={}, products={}",
                events.size(), fresh.size(), productIds.size());
    }

    private record CatalogAggregation(long viewDelta, long likeDelta, ZonedDateTime lastOccurredAt) {}
    private record OrderAggregation(long salesDelta, long amountDelta, ZonedDateTime lastOccurredAt) {}
}

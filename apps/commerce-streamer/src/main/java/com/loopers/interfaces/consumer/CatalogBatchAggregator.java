package com.loopers.interfaces.consumer;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 배치 내 catalog-events를 메모리에서 집계한다.
 * 같은 productId에 대한 여러 이벤트를 단일 delta로 합산.
 */
public class CatalogBatchAggregator {

    private final Map<Long, LikeDelta> likeDeltas = new HashMap<>();
    private final Map<Long, Long> viewIncrements = new HashMap<>();
    private final Set<Long> processedEventIds = new HashSet<>();

    public void addLikeEvent(Long productId, long likeCount, LocalDateTime occurredAt, Long eventId) {
        // 스냅샷 방식: 같은 productId는 마지막 값이 최신
        likeDeltas.put(productId, new LikeDelta(likeCount, occurredAt));
        processedEventIds.add(eventId);
    }

    public void addViewEvent(Long productId, Long eventId) {
        viewIncrements.merge(productId, 1L, Long::sum);
        processedEventIds.add(eventId);
    }

    public Map<Long, LikeDelta> getLikeDeltas() { return likeDeltas; }
    public Map<Long, Long> getViewIncrements() { return viewIncrements; }
    public Set<Long> getProcessedEventIds() { return processedEventIds; }
    public boolean isEmpty() { return likeDeltas.isEmpty() && viewIncrements.isEmpty(); }

    public record LikeDelta(long likeCount, LocalDateTime occurredAt) {}
}

package com.loopers.domain.queue;

import java.util.Optional;
import java.util.List;

public interface WaitingQueueRepository {

    /**
     * 정원을 넘지 않을 때만 ZADD. {@code maxWaiting == 0}이면 정원 검사를 하지 않는다(테스트·하위 호환).
     */
    WaitingQueueJoinResult addIfAbsentWithinCapacity(String eventId, Long userId, long score, long maxWaiting);

    boolean addIfAbsent(String eventId, Long userId, long score);

    Optional<Long> findRank(String eventId, Long userId);

    long countWaiting(String eventId);

    /**
     * ZRANK·ZCARD를 원자적으로 조회한다. 멤버가 없으면 empty.
     */
    Optional<QueuePositionSnapshot> findPositionSnapshot(String eventId, Long userId);

    List<Long> popOldest(String eventId, long count);
}


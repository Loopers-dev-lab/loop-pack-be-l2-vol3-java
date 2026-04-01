package com.loopers.domain.queue;

import com.loopers.support.enums.QueueStatus;

/**
 * 순번 조회 API 결과. 유저의 대기 상태에 따라 정적 팩토리로 생성.
 *
 * <p>기존 프로젝트의 record 패턴(OrderItemSnapshot 등)을 따른다.</p>
 *
 * @param status              대기 상태 (WAITING, READY, NOT_IN_QUEUE)
 * @param position            0-based 순번 (WAITING 시), -1 (NOT_IN_QUEUE 시), 0 (READY 시)
 * @param totalWaiting        전체 대기 인원 (ZCARD)
 * @param estimatedWaitSeconds 예상 대기 시간 (초)
 * @param token               입장 토큰 (READY 상태에서만 non-null)
 */
public record QueuePosition(
        QueueStatus status,
        long position,
        long totalWaiting,
        int estimatedWaitSeconds,
        String token
) {
    /**
     * WAITING 상태 생성. 대기열에 있고 토큰 미발급.
     */
    public static QueuePosition waiting(long position, long totalWaiting, int estimatedSeconds) {
        return new QueuePosition(QueueStatus.WAITING, position, totalWaiting, estimatedSeconds, null);
    }

    /**
     * READY 상태 생성. 토큰 발급 완료, 주문 가능.
     */
    public static QueuePosition ready(long totalWaiting, String token) {
        return new QueuePosition(QueueStatus.READY, 0, totalWaiting, 0, token);
    }

    /**
     * NOT_IN_QUEUE 상태 생성. 대기열 미등록.
     */
    public static QueuePosition notInQueue(long totalWaiting) {
        return new QueuePosition(QueueStatus.NOT_IN_QUEUE, -1, totalWaiting, -1, null);
    }
}

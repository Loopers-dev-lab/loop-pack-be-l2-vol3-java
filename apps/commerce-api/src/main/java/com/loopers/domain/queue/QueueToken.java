package com.loopers.domain.queue;

import java.time.Instant;
import java.util.UUID;

/**
 * 대기열 진입 시 발급되는 Value Object.
 *
 * 역할:
 * - token(UUID)은 클라이언트가 폴링 시 자신을 식별하는 키
 * - joinedAt은 Redis Sorted Set의 score로 사용 → FIFO 순서 보장
 *
 * 설계 선택:
 * - JPA Entity가 아닌 순수 도메인 VO로 설계. DB에 저장하지 않음.
 * - 상태는 Redis가 관리 (queue-token:{token} → userId, TTL)
 * - 서비스가 단일 서버이므로 JWT 대신 UUID 기반 토큰 사용.
 *   서비스 분리 시 JWT로 교체 가능한 구조.
 */
public record QueueToken(
    String token,     // UUID — Redis key: queue-token:{token}
    Long userId,      // 누구의 토큰인지
    String queueId,   // 어느 대기열인지 (e.g. "bf-2024")
    long joinedAt     // 진입 시각 (epoch millis) — ZADD score
) {

    public static QueueToken create(Long userId, String queueId) {
        if (userId == null) throw new IllegalArgumentException("userId는 필수입니다.");
        if (queueId == null || queueId.isBlank()) throw new IllegalArgumentException("queueId는 필수입니다.");

        return new QueueToken(
            UUID.randomUUID().toString(),
            userId,
            queueId,
            Instant.now().toEpochMilli()
        );
    }
}

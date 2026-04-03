package com.loopers.domain.queue;

import java.util.Optional;

/**
 * 대기열 저장소 인터페이스.
 *
 * Domain이 Redis에 직접 의존하지 않도록 DIP 적용.
 * 구현체(QueueRedisRepository)는 Infrastructure에 위치.
 *
 * 핵심 설계 원칙:
 * - 진입/폴링은 stateless: 각 요청이 독립적으로 처리됨
 * - 입장 허가(admit)는 원자적: Lua 스크립트로 ZREM + SET를 단일 연산으로 처리
 *   → ZREM과 SET 사이 중간 상태 제거 (정상 유저 403 방지)
 */
public interface QueueRepository {

    /**
     * 대기열 진입.
     * Redis Sorted Set에 ZADD (score = joinedAt → FIFO 보장).
     * Redis String에 SET queue-token:{token} {userId} EX 3600 (폴링용 토큰).
     * 이미 존재하면 score만 업데이트 → 재진입 시 후순위 배치 (공정성 유지).
     */
    void enter(QueueToken token);

    /**
     * 토큰으로 userId 조회.
     * 토큰이 없거나 TTL 만료 → empty 반환 → 재진입 유도.
     */
    Optional<Long> getUserIdByToken(String token);

    /**
     * 현재 순위 조회 (0-based).
     * Redis ZRANK O(log N) — 10만 명에서도 마이크로초 응답.
     */
    Optional<Long> getRank(String queueId, Long userId);

    /**
     * 전체 대기 인원 조회.
     * Redis ZCARD O(1).
     */
    long getTotalSize(String queueId);

    /**
     * 입장 허가 — 핵심 원자 연산.
     * Lua 스크립트: ZRANK 확인 → ZREM(대기열 제거) → SET entered:{userId} 1 EX 300
     * 두 연산 사이 중간 상태가 존재하지 않음 (Redis 싱글 스레드 보장).
     * @return true: 입장 허가됨 / false: 아직 순번 아님
     */
    boolean admit(String queueId, Long userId, long threshold);

    /**
     * 입장 허가 여부 확인. Service API의 게이트키퍼.
     * EXISTS entered:{userId} — 없으면 403.
     */
    boolean isEntered(Long userId);
}

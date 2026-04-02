package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

/**
 * 대기열 Repository 인터페이스
 *
 * 대기열과 입장 토큰의 저장/조회를 도메인 언어로 정의한다.
 * 구현체는 Infrastructure Layer에서 Redis로 구현한다.
 */
public interface QueueRepository {

    /**
     * 대기열에 사용자를 추가한다 (이미 존재하면 무시 — NX)
     *
     * @return true: 새로 추가됨, false: 이미 대기 중
     */
    boolean addToWaitingQueue(Long userId, double score);

    /**
     * 대기열에서 사용자의 현재 순번을 조회한다 (0-based)
     *
     * @return 순번 (대기열에 없으면 empty)
     */
    Optional<Long> getPosition(Long userId);

    /**
     * 전체 대기 인원을 조회한다
     */
    long getTotalWaiting();

    /**
     * 대기열 앞쪽에서 count명을 꺼내고 입장 토큰을 발급한다 (원자적)
     *
     * @return 활성화된 userId 목록
     */
    List<Long> activateFromQueue(int count, int tokenTtlSeconds);

    /**
     * 사용자의 입장 토큰이 유효한지 확인한다
     */
    boolean hasValidToken(Long userId);

    /**
     * 사용자의 입장 토큰 잔여 TTL을 조회한다 (초 단위)
     *
     * @return TTL 초 (토큰이 없으면 -2, TTL 없으면 -1)
     */
    long getTokenTtl(Long userId);

    /**
     * 사용자의 입장 토큰을 삭제한다
     */
    void deleteToken(Long userId);

    /**
     * 대기열에서 사용자를 제거한다 (취소)
     */
    void removeFromWaitingQueue(Long userId);

    /**
     * 현재 활성 토큰 수를 조회한다
     */
    long getActiveTokenCount();
}

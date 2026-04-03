package com.loopers.domain.queue;

import java.util.List;

/**
 * 대기열 저장소 인터페이스. Redis Sorted Set 기반으로 infrastructure에서 구현.
 *
 * <p>기존 {@code OrderRepository}, {@code UserRepository}와 동일한 DIP 패턴.
 * 도메인 레이어는 Redis에 의존하지 않는다.</p>
 */
public interface QueueRepository {

    /**
     * 대기열에 사용자를 추가한다. 이미 존재하면 무시 (ZADD NX).
     *
     * @param userId 사용자 ID
     * @param score  진입 시각 (밀리초 타임스탬프)
     * @return true=신규 추가, false=이미 존재
     */
    boolean addIfAbsent(Long userId, double score);

    /**
     * 사용자의 현재 순번을 조회한다 (ZRANK).
     *
     * @param userId 사용자 ID
     * @return 0-based 순번, 대기열에 없으면 null
     */
    Long getRank(Long userId);

    /**
     * 전체 대기 인원을 조회한다 (ZCARD).
     *
     * @return 대기 인원 수
     */
    long getSize();

    /**
     * 앞에서부터 count명을 원자적으로 제거하고 반환한다 (ZPOPMIN).
     *
     * @param count 꺼낼 인원 수
     * @return 꺼낸 항목 리스트 (비어있을 수 있음)
     */
    List<QueueEntry> popMin(int count);

    /**
     * 순번, 대기 인원, 토큰을 원자적으로 조회한다 (Lua script 1 RTT).
     *
     * @param userId   사용자 ID
     * @param tokenKey 토큰 Redis 키
     * @return [rank(Long 또는 null), size(Long), token(String 또는 null)]
     */
    PositionSnapshot getPositionSnapshot(Long userId, String tokenKey);

    /**
     * 순번 조회 스냅샷. Lua script 결과를 담는 record.
     */
    record PositionSnapshot(Long rank, long size, String token) {}

    /**
     * Master에서 토큰을 조회한다. Replica 지연으로 NOT_IN_QUEUE 판정 시 Master 재확인용.
     *
     * <p>getPositionSnapshot은 Replica에서 실행되므로, ZPOPMIN 직후 토큰이 SET됐지만
     * Replica에 아직 반영되지 않은 경우 NOT_IN_QUEUE로 잘못 판정될 수 있다.
     * 이 메서드로 Master에서 토큰을 재확인하여 READY 상태를 놓치지 않도록 한다.</p>
     *
     * @param userId 사용자 ID
     * @return 토큰 값 또는 null
     */
    String getTokenFromMaster(Long userId);
}

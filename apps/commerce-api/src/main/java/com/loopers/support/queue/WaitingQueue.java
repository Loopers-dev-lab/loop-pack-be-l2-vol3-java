package com.loopers.support.queue;

/**
 * 대기열 관리를 위한 포트 인터페이스.
 *
 * <p>진입 순서를 보장하며, 동일 사용자의 중복 진입을 방지한다.
 * 인프라스트럭처 계층에서 구체적인 저장소(Redis 등)로 구현한다.</p>
 */
public interface WaitingQueue {

    /**
     * 대기열에 진입한다.
     *
     * <p>이미 대기열에 존재하는 사용자는 재진입되지 않는다.</p>
     *
     * @param userId 사용자 ID
     * @return 진입 성공 시 {@code true}, 이미 대기 중이면 {@code false}
     */
    boolean enter(Long userId);

    /**
     * 대기열에서의 순번을 조회한다.
     *
     * @param userId 사용자 ID
     * @return 0-based 순번, 대기열에 없으면 {@code null}
     */
    Long getPosition(Long userId);

    /**
     * 전체 대기 인원을 조회한다.
     *
     * @return 현재 대기열에 있는 총 인원 수
     */
    long getTotalCount();
}

package com.loopers.domain.queue;

import java.util.List;
import java.util.Optional;

// 대기열 저장소 인터페이스. Domain 레이어에 정의하여 DIP를 적용한다.
// 구현체(QueueRedisRepository)에서 Redis Sorted Set을 사용하며,
// score(타임스탬프)로 진입 순서를 보장한다.
public interface QueueRepository {

    // 대기열에 유저를 추가한다. score는 진입 시각(ms)으로 선착순 정렬에 사용된다.
    // NX 옵션: 이미 대기열에 있는 유저는 중복 추가하지 않는다. 성공 시 true 반환.
    boolean enter(Long userId, double score);

    // 대기열 내 유저의 순번을 조회한다 (0-based, Redis ZRANK).
    // 대기열에 없는 유저는 Optional.empty()를 반환한다.
    Optional<Long> getRank(Long userId);

    // 현재 대기열에 존재하는 전체 유저 수를 반환한다.
    long getTotalCount();

    // 대기열 앞쪽에서 count명을 원자적으로 꺼낸다 (ZPOPMIN).
    // 조회와 동시에 대기열에서 제거되므로 peek+remove보다 안전하다.
    // 토큰 발급 실패 시 enter()로 원래 score를 사용해 재삽입해야 한다.
    List<QueueEntry> popFront(int count);
}

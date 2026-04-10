package com.loopers.application.ranking;

import java.util.Optional;

/**
 * 랭킹 캐시 포트 (Secondary Port / Driven Port).
 * application 레이어는 이 인터페이스에만 의존 → Redis 등 구현 기술이 바뀌어도 Facade 코드 변경 없음.
 */
public interface RankingCacheRepository {

    // 캐시 조회 - 미스 시 Optional.empty() 반환 (예외 미전파)
    Optional<RankingResult> get(String cacheKey);

    // 캐시 저장 (Redis 장애 시에도 예외 미전파)
    void save(String cacheKey, RankingResult result);
}

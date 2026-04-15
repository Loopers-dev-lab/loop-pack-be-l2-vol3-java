package com.loopers.domain.ranking.mv;

import java.time.LocalDate;
import java.util.List;

/**
 * LAST_7D / LAST_30D MV 에 대한 조회 전용 Repository.
 * Redis identity cache miss 시 fallback 경로가 여기로 진입한다.
 */
public interface MvRankingQueryRepository {

    // Query
    List<MvRankEntry> findLast7d(LocalDate anchorDate, String weightGroup, int offset, int limit);

    List<MvRankEntry> findLast30d(LocalDate anchorDate, String weightGroup, int offset, int limit);

    long countLast7d(LocalDate anchorDate, String weightGroup);

    long countLast30d(LocalDate anchorDate, String weightGroup);
}

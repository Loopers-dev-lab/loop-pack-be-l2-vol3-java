package com.loopers.domain.ranking;

import java.util.List;
import java.util.Optional;

/**
 * 주간/월간 랭킹 MV 읽기 포트. 구현은 infrastructure.
 * <p>
 * 조회는 요청당 {@code MAX(version)}을 한 번 정한 뒤 동일 버전 행만 읽는다(로드맵 3.6 active version 고정).
 */
public interface RankingMvReadRepository {

    Optional<Integer> findMaxVersionForWeekly(String periodKey);

    Optional<Integer> findMaxVersionForMonthly(String periodKey);

    List<RankingMvTableRow> findWeeklyByPeriodKeyAndVersionOrdered(String periodKey, int version);

    List<RankingMvTableRow> findMonthlyByPeriodKeyAndVersionOrdered(String periodKey, int version);
}

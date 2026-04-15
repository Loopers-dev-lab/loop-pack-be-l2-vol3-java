package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

/**
 * 주간 랭킹 데이터를 조회하는 포트.
 *
 * <p>배치가 집계한 {@code mv_product_rank_weekly} 테이블에서 랭킹 데이터를 읽기 전용으로 제공한다.</p>
 */
public interface WeeklyRankingRepository {

    /**
     * 지정한 scoreDate의 주간 랭킹을 점수 내림차순으로 조회한다.
     *
     * @param scoreDate 조회 기준일
     * @param page      페이지 번호 (0-based)
     * @param size      페이지 크기
     * @return 주간 랭킹 엔티티 목록 (점수 내림차순)
     */
    List<ProductRankingWeekly> readTopRanked(LocalDate scoreDate, int page, int size);
}

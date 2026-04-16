package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

/**
 * MV 랭킹 테이블 쓰기 전용 저장소 인터페이스 (DIP).
 *
 * 배치 Writer 는 이 인터페이스에만 의존하며, JDBC 구현 세부 사항을 알지 못한다.
 */
public interface MvProductRankRepository {

    /**
     * 주간 MV 테이블을 해당 baseDate 기준으로 교체한다 (DELETE + INSERT).
     *
     * @param baseDate 기준일 (targetDate - 1일)
     * @param rows     rank 1 부터 순서대로 정렬된 집계 결과
     */
    void replaceWeeklyRanking(LocalDate baseDate, List<MvProductRankRow> rows);

    /**
     * 월간 MV 테이블을 해당 baseDate 기준으로 교체한다 (DELETE + INSERT).
     *
     * @param baseDate 기준일 (targetDate - 1일)
     * @param rows     rank 1 부터 순서대로 정렬된 집계 결과
     */
    void replaceMonthlyRanking(LocalDate baseDate, List<MvProductRankRow> rows);
}

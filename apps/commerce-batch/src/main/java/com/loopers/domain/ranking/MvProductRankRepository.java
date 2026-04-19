package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

/**
 * MV 랭킹 테이블 쓰기 전용 저장소 인터페이스 (DIP).
 *
 * 배치 Writer 는 이 인터페이스에만 의존하며, JDBC 구현 세부 사항을 알지 못한다.
 *
 * 공통 파라미터 계약:
 * - baseDate, rows 는 null 을 허용하지 않는다. null 전달 시 IllegalArgumentException 이 발생한다.
 * - rows 가 빈 리스트이면 DELETE 만 실행하고 INSERT 는 건너뛴다.
 *   같은 base_date 로 재실행 시 집계 결과가 없으면 기존 MV 행이 정리된다.
 */
public interface MvProductRankRepository {

    /**
     * 주간 MV 테이블을 해당 baseDate 기준으로 교체한다 (DELETE + INSERT).
     *
     * @param baseDate 기준일 (targetDate - 1일), null 불가
     * @param rows     rank 1 부터 순서대로 정렬된 집계 결과, null 불가. 빈 리스트이면 DELETE 만 실행
     */
    void replaceWeeklyRanking(LocalDate baseDate, List<MvProductRankRow> rows);

    /**
     * 월간 MV 테이블을 해당 baseDate 기준으로 교체한다 (DELETE + INSERT).
     *
     * @param baseDate 기준일 (targetDate - 1일), null 불가
     * @param rows     rank 1 부터 순서대로 정렬된 집계 결과, null 불가. 빈 리스트이면 DELETE 만 실행
     */
    void replaceMonthlyRanking(LocalDate baseDate, List<MvProductRankRow> rows);

    /**
     * 주간 MV 테이블에서 해당 baseDate 행을 삭제한다.
     *
     * @param baseDate 삭제할 기준일, null 불가
     */
    void deleteWeeklyByBaseDate(LocalDate baseDate);

    /**
     * 월간 MV 테이블에서 해당 baseDate 행을 삭제한다.
     *
     * @param baseDate 삭제할 기준일, null 불가
     */
    void deleteMonthlyByBaseDate(LocalDate baseDate);
}

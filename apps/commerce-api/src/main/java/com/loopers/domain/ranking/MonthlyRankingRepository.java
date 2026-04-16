package com.loopers.domain.ranking;

import java.time.LocalDate;
import java.util.List;

/**
 * 월간 MV 랭킹 읽기 전용 저장소 인터페이스 (DIP).
 *
 * commerce-batch 가 mv_product_rank_monthly 에 적재한 데이터를 조회한다.
 * base_date = batch 실행일 - 1일 (어제 기준 직전 30일 집계 결과).
 */
public interface MonthlyRankingRepository {

    /**
     * 지정 baseDate 의 월간 TOP-N 을 반환한다. rank 는 1-based.
     *
     * @param baseDate     집계 기준일 (MV 테이블의 base_date)
     * @param pageOneBased 사용자 노출 기준 페이지 번호 (1-based)
     * @param size         페이지 크기
     * @return 해당 날짜의 월간 랭킹 엔트리. 데이터가 없으면 빈 리스트.
     */
    List<RankingEntry> getTopN(LocalDate baseDate, int pageOneBased, int size);

    /**
     * 지정 baseDate 의 월간 랭킹 전체 엔트리 수.
     */
    long getTotal(LocalDate baseDate);
}

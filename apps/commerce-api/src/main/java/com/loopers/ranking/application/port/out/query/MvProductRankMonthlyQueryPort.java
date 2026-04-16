package com.loopers.ranking.application.port.out.query;


import com.loopers.ranking.application.dto.out.RankingPageOutDto;

import java.time.LocalDate;


/**
 * 월간 랭킹 MV 조회 포트
 */
public interface MvProductRankMonthlyQueryPort {

	/**
	 * month_start_date + scorer_type 기준 월간 TOP 랭킹 페이지 조회
	 */
	RankingPageOutDto findByMonthStartDate(LocalDate monthStartDate, String scorerType, int page, int size);

}

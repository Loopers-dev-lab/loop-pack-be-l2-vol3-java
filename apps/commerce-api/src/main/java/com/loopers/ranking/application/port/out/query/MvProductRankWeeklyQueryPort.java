package com.loopers.ranking.application.port.out.query;


import com.loopers.ranking.application.dto.out.RankingPageOutDto;

import java.time.LocalDate;


/**
 * 주간 랭킹 MV 조회 포트
 */
public interface MvProductRankWeeklyQueryPort {

	/**
	 * week_start_date + scorer_type 기준 주간 TOP 랭킹 페이지 조회
	 */
	RankingPageOutDto findByWeekStartDate(LocalDate weekStartDate, String scorerType, int page, int size);

}

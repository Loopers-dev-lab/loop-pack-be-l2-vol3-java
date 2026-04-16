package com.loopers.ranking.application.service;


import com.loopers.ranking.application.dto.out.RankingPageOutDto;
import com.loopers.ranking.application.port.out.query.MvProductRankMonthlyQueryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;


/**
 * 월간 랭킹 조회 서비스
 * - mv_product_rank_monthly 기반 조회
 * - dateStr 기준으로 month_start_date(1일) 계산
 * - dateStr null/blank → 어제 날짜
 */

@Service
@RequiredArgsConstructor
public class MonthlyRankingQueryService {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
	private static final String DEFAULT_SCORER_TYPE = "SATURATION";

	// query port
	private final MvProductRankMonthlyQueryPort mvProductRankMonthlyQueryPort;


	/**
	 * 1. 월간 랭킹 조회
	 * - dateStr: yyyyMMdd 형식 또는 null/blank (기본값: 어제)
	 * - page: 0-based
	 */
	@Transactional(readOnly = true)
	public RankingPageOutDto getRankings(String dateStr, int page, int size) {
		LocalDate date = resolveDate(dateStr);
		LocalDate monthStart = date.withDayOfMonth(1);

		return mvProductRankMonthlyQueryPort.findByMonthStartDate(monthStart, DEFAULT_SCORER_TYPE, page, size);
	}


	/**
	 * private method
	 * - 날짜 문자열 해석 (null/blank → 어제)
	 */

	// 날짜 문자열 해석
	private LocalDate resolveDate(String dateStr) {
		if (dateStr == null || dateStr.isBlank()) {
			return LocalDate.now().minusDays(1);
		}
		return LocalDate.parse(dateStr, DATE_FORMAT);
	}

}

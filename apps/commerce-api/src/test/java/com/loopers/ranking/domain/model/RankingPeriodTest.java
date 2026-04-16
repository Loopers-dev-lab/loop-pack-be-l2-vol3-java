package com.loopers.ranking.domain.model;


import com.loopers.support.common.error.CoreException;
import com.loopers.support.common.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;


@DisplayName("RankingPeriod 단위 테스트")
class RankingPeriodTest {

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = {"  "})
	@DisplayName("[RankingPeriod.from()] null/blank 입력 -> DAILY 반환 (기존 호환)")
	void nullOrBlank_returnsDaily(String value) {
		// Act
		RankingPeriod result = RankingPeriod.from(value);

		// Assert
		assertThat(result).isEqualTo(RankingPeriod.DAILY);
	}


	@Test
	@DisplayName("[RankingPeriod.from()] 'daily' 입력 -> DAILY 반환")
	void daily_returnsDailyPeriod() {
		assertThat(RankingPeriod.from("daily")).isEqualTo(RankingPeriod.DAILY);
	}


	@Test
	@DisplayName("[RankingPeriod.from()] 'weekly' 입력 -> WEEKLY 반환")
	void weekly_returnsWeeklyPeriod() {
		assertThat(RankingPeriod.from("weekly")).isEqualTo(RankingPeriod.WEEKLY);
	}


	@Test
	@DisplayName("[RankingPeriod.from()] 'monthly' 입력 -> MONTHLY 반환")
	void monthly_returnsMonthlyPeriod() {
		assertThat(RankingPeriod.from("monthly")).isEqualTo(RankingPeriod.MONTHLY);
	}


	@Test
	@DisplayName("[RankingPeriod.from()] 대문자 'DAILY' 입력 -> DAILY 반환 (대소문자 무관)")
	void uppercase_returnsDailyPeriod() {
		assertThat(RankingPeriod.from("DAILY")).isEqualTo(RankingPeriod.DAILY);
	}


	@Test
	@DisplayName("[RankingPeriod.from()] 지원하지 않는 값 -> INVALID_RANKING_PERIOD 예외")
	void invalidValue_throwsCoreException() {
		// Act
		CoreException exception = assertThrows(CoreException.class,
			() -> RankingPeriod.from("yearly"));

		// Assert
		assertAll(
			() -> assertThat(exception.getErrorType()).isEqualTo(ErrorType.INVALID_RANKING_PERIOD),
			() -> assertThat(exception.getMessage()).isEqualTo(ErrorType.INVALID_RANKING_PERIOD.getMessage())
		);
	}

}

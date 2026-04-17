package com.loopers.domain.ranking;

import com.loopers.domain.ranking.model.RankingPeriod;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RankingPeriodTest {

    @DisplayName("period 문자열을 대소문자 구분 없이 enum으로 변환한다")
    @Test
    void from() {
        assertThat(RankingPeriod.from("daily")).isEqualTo(RankingPeriod.DAILY);
        assertThat(RankingPeriod.from("WEEKLY")).isEqualTo(RankingPeriod.WEEKLY);
        assertThat(RankingPeriod.from("Monthly")).isEqualTo(RankingPeriod.MONTHLY);
    }

    @DisplayName("period가 비어있으면 daily를 기본값으로 사용한다")
    @Test
    void defaultDaily() {
        assertThat(RankingPeriod.from(null)).isEqualTo(RankingPeriod.DAILY);
        assertThat(RankingPeriod.from("")).isEqualTo(RankingPeriod.DAILY);
    }

    @DisplayName("지원하지 않는 period는 예외가 발생한다")
    @Test
    void invalid() {
        assertThatThrownBy(() -> RankingPeriod.from("yearly"))
                .isInstanceOf(CoreException.class);
    }
}

package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingApp;
import com.loopers.application.ranking.RankingPageResult;
import com.loopers.domain.ranking.RankingPeriod;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("RankingPeriod 파싱 테스트")
class RankingV1ControllerPeriodTest {

    @DisplayName("null → DAILY")
    @Test
    void nullInput_returnDaily() {
        assertThat(RankingPeriod.fromString(null)).isEqualTo(RankingPeriod.DAILY);
    }

    @DisplayName("빈 문자열 → DAILY")
    @Test
    void emptyInput_returnDaily() {
        assertThat(RankingPeriod.fromString("")).isEqualTo(RankingPeriod.DAILY);
    }

    @DisplayName("daily → DAILY")
    @Test
    void daily() {
        assertThat(RankingPeriod.fromString("daily")).isEqualTo(RankingPeriod.DAILY);
    }

    @DisplayName("weekly → WEEKLY (대소문자 무관)")
    @Test
    void weekly_caseInsensitive() {
        assertThat(RankingPeriod.fromString("weekly")).isEqualTo(RankingPeriod.WEEKLY);
        assertThat(RankingPeriod.fromString("WEEKLY")).isEqualTo(RankingPeriod.WEEKLY);
        assertThat(RankingPeriod.fromString("Weekly")).isEqualTo(RankingPeriod.WEEKLY);
    }

    @DisplayName("monthly → MONTHLY")
    @Test
    void monthly() {
        assertThat(RankingPeriod.fromString("monthly")).isEqualTo(RankingPeriod.MONTHLY);
    }

    @DisplayName("quarterly → QUARTERLY")
    @Test
    void quarterly() {
        assertThat(RankingPeriod.fromString("quarterly")).isEqualTo(RankingPeriod.QUARTERLY);
        assertThat(RankingPeriod.fromString("QUARTERLY")).isEqualTo(RankingPeriod.QUARTERLY);
    }

    @DisplayName("invalid → CoreException(BAD_REQUEST)")
    @Test
    void invalid_throwsException() {
        assertThatThrownBy(() -> RankingPeriod.fromString("invalid"))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                .hasMessageContaining("지원하지 않는 ranking period");
    }

    @DisplayName("date 파라미터가 yyyyMMdd 형식이 아니면 BAD_REQUEST")
    @Test
    void invalidDate_throwsBadRequest() {
        RankingApp app = Mockito.mock(RankingApp.class);
        RankingV1Controller controller = new RankingV1Controller(app);

        assertThatThrownBy(() -> controller.getRankingByOffset(null, "2026-04-01", 0, 20))
                .isInstanceOfSatisfying(CoreException.class,
                        e -> assertThat(e.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST))
                .hasMessageContaining("지원하지 않는 date 형식");

        Mockito.verifyNoInteractions(app);
    }

    @DisplayName("date 파라미터가 yyyyMMdd 형식이면 정상 호출")
    @Test
    void validDate_invokesApp() {
        RankingApp app = Mockito.mock(RankingApp.class);
        Mockito.when(app.getTopN(Mockito.any(), Mockito.any(), Mockito.anyLong(), Mockito.anyLong()))
                .thenReturn(new RankingPageResult(List.of(), 0L, 20L, 0L, null));
        RankingV1Controller controller = new RankingV1Controller(app);

        assertThatCode(() -> controller.getRankingByOffset(null, "20260401", 0, 20))
                .doesNotThrowAnyException();
    }
}

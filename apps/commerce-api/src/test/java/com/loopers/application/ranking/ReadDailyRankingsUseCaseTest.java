package com.loopers.application.ranking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.loopers.domain.ranking.RankingItem;
import com.loopers.domain.ranking.RankingService;
import com.loopers.support.page.PageSize;

@ExtendWith(MockitoExtension.class)
class ReadDailyRankingsUseCaseTest {

    private ReadDailyRankingsUseCase readRankingsUseCase;

    @Mock
    private RankingService rankingService;

    @Mock
    private RankingResultAssembler rankingResultAssembler;

    @BeforeEach
    void setUp() {
        readRankingsUseCase = new ReadDailyRankingsUseCase(rankingService, rankingResultAssembler);
    }

    @DisplayName("랭킹을 조회할 때,")
    @Nested
    class Execute {

        @DisplayName("일간 랭킹을 조회하고 결과를 조합하여 반환한다.")
        @Test
        void delegatesToAssembler() {
            // arrange
            Long userId = 100L;
            String date = "20250406";
            PageSize pageSize = new PageSize(0, 20);

            var items = List.of(
                    new RankingItem(1, 42L, 70.0),
                    new RankingItem(2, 15L, 58.4)
            );
            given(rankingService.readDailyTopRanked(anyString(), anyInt(), anyInt())).willReturn(items);

            var expected = new RankingPageResult(List.of(), 0, 20);
            given(rankingResultAssembler.assemble(userId, items, pageSize)).willReturn(expected);

            // act
            RankingPageResult result = readRankingsUseCase.execute(userId, date, pageSize);

            // assert
            assertThat(result).isEqualTo(expected);
            verify(rankingService).readDailyTopRanked(date, 0, 20);
            verify(rankingResultAssembler).assemble(userId, items, pageSize);
        }
    }
}

package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RankingService (api) 단위 테스트")
class RankingServiceTest {

    @Mock
    private RankingRepository rankingRepository;

    @InjectMocks
    private RankingService rankingService;

    private static final LocalDate DATE = LocalDate.of(2026, 4, 7);

    @Test
    @DisplayName("getRank — 0-based를 1-based로 변환한다")
    void getRank_ConvertsTo1Based() {
        when(rankingRepository.getRank(DATE, 101L)).thenReturn(0L);

        Long rank = rankingService.getRank(DATE, 101L);

        assertThat(rank).isEqualTo(1L);
    }

    @Test
    @DisplayName("getRank — 랭킹에 없으면 null 반환")
    void getRank_ReturnsNull_WhenNotFound() {
        when(rankingRepository.getRank(DATE, 999L)).thenReturn(null);

        Long rank = rankingService.getRank(DATE, 999L);

        assertThat(rank).isNull();
    }

    @Test
    @DisplayName("getTopRankings — 페이지 계산이 정확하다")
    void getTopRankings_CalculatesPageCorrectly() {
        when(rankingRepository.getTopRankings(DATE, 20, 39))
                .thenReturn(List.of(new RankingRepository.RankingEntry(101L, 50.0)));

        List<RankingRepository.RankingEntry> result =
                rankingService.getTopRankings(DATE, 1, 20);

        assertThat(result).hasSize(1);
    }
}

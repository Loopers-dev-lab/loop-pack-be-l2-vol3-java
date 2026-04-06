package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @InjectMocks
    private RankingService rankingService;

    @Mock
    private RankingRepository rankingRepository;

    @Nested
    @DisplayName("랭킹 조회")
    class GetTopRankings {

        @Test
        @DisplayName("date, page, size를 기반으로 상위 랭킹을 조회한다")
        void getTopRankings() {
            String date = "20260406";
            given(rankingRepository.getTopRankings("20260406", 0, 20))
                    .willReturn(List.of(
                            new RankingRepository.ProductScore(2L, 200.0),
                            new RankingRepository.ProductScore(1L, 100.0)
                    ));

            List<RankingRepository.ProductScore> result = rankingService.getTopRankings(date, 1, 20);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).productId()).isEqualTo(2L);
        }

        @Test
        @DisplayName("page가 2이면 offset이 size만큼 증가한다")
        void getTopRankingsPage2() {
            String date = "20260406";
            given(rankingRepository.getTopRankings("20260406", 20, 20))
                    .willReturn(List.of(
                            new RankingRepository.ProductScore(3L, 50.0)
                    ));

            List<RankingRepository.ProductScore> result = rankingService.getTopRankings(date, 2, 20);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).productId()).isEqualTo(3L);
        }
    }

    @Nested
    @DisplayName("상품 순위 조회")
    class GetRankForProduct {

        @Test
        @DisplayName("특정 상품의 순위를 반환한다")
        void getRank() {
            given(rankingRepository.getRank("20260406", 1L)).willReturn(3L);

            Long rank = rankingService.getRankForProduct("20260406", 1L);

            assertThat(rank).isEqualTo(3L);
        }

        @Test
        @DisplayName("랭킹에 없는 상품은 null을 반환한다")
        void rankNotFound() {
            given(rankingRepository.getRank("20260406", 999L)).willReturn(null);

            Long rank = rankingService.getRankForProduct("20260406", 999L);

            assertThat(rank).isNull();
        }
    }
}

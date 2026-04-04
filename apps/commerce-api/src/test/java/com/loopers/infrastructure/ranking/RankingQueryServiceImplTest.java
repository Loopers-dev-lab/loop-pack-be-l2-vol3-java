package com.loopers.infrastructure.ranking;

import com.loopers.domain.PageResult;
import com.loopers.domain.ranking.FakeRankingRepository;
import com.loopers.domain.ranking.ProductRanking;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RankingQueryServiceImplTest {

    private FakeRankingRepository rankingRepository;
    private RankingQueryServiceImpl rankingQueryService;

    @BeforeEach
    void setUp() {
        rankingRepository = new FakeRankingRepository();
        rankingQueryService = new RankingQueryServiceImpl(rankingRepository);
    }

    @DisplayName("일별 랭킹 조회할 때, ")
    @Nested
    class GetDailyRanking {

        @DisplayName("페이징된 결과를 반환한다.")
        @Test
        void returnsPaginatedResult() {
            String key = "ranking:day:20260404";
            for (long i = 1; i <= 5; i++) {
                rankingRepository.addScore(key, i, i * 10.0);
            }

            PageResult<ProductRanking> result = rankingQueryService.getDailyRanking("20260404", 0, 2);

            assertThat(result.items()).hasSize(2);
            assertThat(result.items().get(0).productId()).isEqualTo(5L);
            assertThat(result.items().get(0).rank()).isEqualTo(1);
            assertThat(result.totalElements()).isEqualTo(5);
            assertThat(result.totalPages()).isEqualTo(3);
        }

        @DisplayName("데이터가 없으면 빈 결과를 반환한다.")
        @Test
        void returnsEmpty_whenNoData() {
            PageResult<ProductRanking> result = rankingQueryService.getDailyRanking("20260404", 0, 20);

            assertThat(result.items()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }
    }

    @DisplayName("개별 상품 순위 조회할 때, ")
    @Nested
    class GetProductDailyRank {

        @DisplayName("해당 상품의 1-based 순위를 반환한다.")
        @Test
        void returnsRank() {
            rankingRepository.addScore("ranking:day:20260404", 101L, 100.0);
            rankingRepository.addScore("ranking:day:20260404", 102L, 50.0);

            Optional<Long> rank = rankingQueryService.getProductDailyRank(101L, "20260404");

            assertThat(rank).isPresent().contains(1L);
        }

        @DisplayName("랭킹에 없으면 empty를 반환한다.")
        @Test
        void returnsEmpty_whenNotInRanking() {
            Optional<Long> rank = rankingQueryService.getProductDailyRank(999L, "20260404");

            assertThat(rank).isEmpty();
        }
    }
}

package com.loopers.domain.ranking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingServiceTest {

    @InjectMocks
    private RankingService rankingService;

    @Mock
    private RankingRepository rankingRepository;

    private String todayKey() {
        return "ranking:all:" + LocalDate.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd"));
    }

    @Nested
    @DisplayName("Top-N 랭킹 조회")
    class GetTopRankings {

        @Test
        @DisplayName("page=1, size=2 요청 시 offset=0부터 2개를 조회한다")
        void getTopRankings() {
            // given
            String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            List<RankingEntry> entries = List.of(
                    new RankingEntry(1L, 10.0),
                    new RankingEntry(2L, 8.0)
            );
            when(rankingRepository.getTopN(eq("ranking:all:" + date), eq(0), eq(2)))
                    .thenReturn(entries);

            // when
            List<RankingEntry> result = rankingService.getTopRankings(date, 1, 2);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).productId()).isEqualTo(1L);
        }
    }

    @Nested
    @DisplayName("개별 상품 순위 조회")
    class GetProductRank {

        @Test
        @DisplayName("ZSET에 있는 상품은 1-based 순위를 반환한다")
        void existingProduct() {
            // given
            when(rankingRepository.getRank(eq(todayKey()), eq(1L)))
                    .thenReturn(0L); // 0-based

            // when
            Long rank = rankingService.getProductRank(1L);

            // then
            assertThat(rank).isEqualTo(1L); // 1-based
        }

        @Test
        @DisplayName("ZSET에 없는 상품은 null을 반환한다")
        void nonExistingProduct() {
            // given
            when(rankingRepository.getRank(eq(todayKey()), eq(999L)))
                    .thenReturn(null);

            // when
            Long rank = rankingService.getProductRank(999L);

            // then
            assertThat(rank).isNull();
        }
    }
}

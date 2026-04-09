package com.loopers.application.ranking;

import com.loopers.application.product.ProductFacade;
import com.loopers.application.product.ProductInfo;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RankingFacade 단위 테스트")
class RankingFacadeTest {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final LocalDate TODAY = LocalDate.of(2026, 4, 9);
    private static final Clock FIXED =
            Clock.fixed(TODAY.atStartOfDay(KST).plusHours(10).toInstant(), KST);

    private RankingRepository rankingRepository;
    private ProductFacade productFacade;
    private RankingFacade facade;

    @BeforeEach
    void setUp() {
        rankingRepository = mock(RankingRepository.class);
        productFacade = mock(ProductFacade.class);
        facade = new RankingFacade(rankingRepository, productFacade, FIXED);
    }

    private ProductInfo stubProduct(Long id) {
        return new ProductInfo(id, 1L, "브랜드", "상품" + id, 10000, 9000, 2500, 0,
                ProductStatus.ON_SALE, "Y", ZonedDateTime.now());
    }

    @Nested
    @DisplayName("getDailyRanking")
    class GetDailyRanking {

        @Test
        @DisplayName("ZSET Top-N 과 상품 정보를 Aggregation 하여 반환")
        void happyPath() {
            // given
            List<RankingEntry> entries = List.of(
                    new RankingEntry(1L, 1L, 5.0),
                    new RankingEntry(2L, 2L, 3.0)
            );
            when(rankingRepository.getTopN("ranking:all:20260409", 1, 20)).thenReturn(entries);
            when(productFacade.findVisibleByIds(List.of(1L, 2L))).thenReturn(Map.of(
                    1L, stubProduct(1L),
                    2L, stubProduct(2L)
            ));

            // when
            List<RankingItemInfo> result = facade.getDailyRanking(TODAY, 1, 20);

            // then
            assertThat(result).hasSize(2);
            assertThat(result.get(0).rank()).isEqualTo(1L);
            assertThat(result.get(0).product().id()).isEqualTo(1L);
            assertThat(result.get(1).rank()).isEqualTo(2L);
            assertThat(result.get(1).product().id()).isEqualTo(2L);
        }

        @Test
        @DisplayName("삭제/숨김 상품은 응답에서 제외되고 size 는 축소된다")
        void visibilityFilter() {
            // given — 3개 엔트리, 2번 상품만 visible
            List<RankingEntry> entries = List.of(
                    new RankingEntry(1L, 1L, 5.0),
                    new RankingEntry(2L, 2L, 4.0),
                    new RankingEntry(3L, 3L, 3.0)
            );
            when(rankingRepository.getTopN(any(), anyInt(), anyInt())).thenReturn(entries);
            when(productFacade.findVisibleByIds(List.of(1L, 2L, 3L)))
                    .thenReturn(Map.of(2L, stubProduct(2L)));

            // when
            List<RankingItemInfo> result = facade.getDailyRanking(TODAY, 1, 20);

            // then — 2번만 남음
            assertThat(result).hasSize(1);
            assertThat(result.get(0).product().id()).isEqualTo(2L);
            assertThat(result.get(0).rank()).isEqualTo(2L); // 원 rank 유지
        }

        @Test
        @DisplayName("date 가 null 이면 KST 오늘 날짜로 조회")
        void nullDateDefaultsToToday() {
            // given
            when(rankingRepository.getTopN(any(), anyInt(), anyInt())).thenReturn(List.of());

            // when
            facade.getDailyRanking(null, 1, 20);

            // then
            verify(rankingRepository).getTopN(eq("ranking:all:20260409"), eq(1), eq(20));
        }

        @Test
        @DisplayName("ZSET 이 비어 있으면 빈 리스트")
        void emptyRanking() {
            // given
            when(rankingRepository.getTopN(any(), anyInt(), anyInt())).thenReturn(List.of());

            // when
            List<RankingItemInfo> result = facade.getDailyRanking(TODAY, 1, 20);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("getDailyRank")
    class GetDailyRank {

        @Test
        @DisplayName("ZREVRANK 결과를 그대로 반환")
        void returnsRank() {
            // given
            when(rankingRepository.getRank("ranking:all:20260409", 100L)).thenReturn(5L);

            // when
            Long rank = facade.getDailyRank(100L);

            // then
            assertThat(rank).isEqualTo(5L);
        }

        @Test
        @DisplayName("순위권 밖이면 null")
        void nullWhenAbsent() {
            // given
            when(rankingRepository.getRank(any(), any())).thenReturn(null);

            // when
            Long rank = facade.getDailyRank(100L);

            // then
            assertThat(rank).isNull();
        }

        @Test
        @DisplayName("productId 가 null 이면 null 반환")
        void nullProductId() {
            assertThat(facade.getDailyRank(null)).isNull();
        }
    }
}

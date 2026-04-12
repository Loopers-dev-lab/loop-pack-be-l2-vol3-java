package com.loopers.application.ranking;

import com.loopers.domain.ranking.MvProductRankRepository;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("RankingApp 단위 테스트 (commerce-api)")
class RankingAppTest {

    private RankingRepository rankingRepository;
    private MvProductRankRepository mvProductRankRepository;
    private RankingProductCache productCache;
    private RankingApp rankingApp;

    @BeforeEach
    void setUp() {
        rankingRepository = mock(RankingRepository.class);
        mvProductRankRepository = mock(MvProductRankRepository.class);
        productCache = mock(RankingProductCache.class);
        rankingApp = new RankingApp(rankingRepository, mvProductRankRepository, productCache);
    }

    @Nested
    @DisplayName("getTopN()")
    class GetTopN {

        @Test
        @DisplayName("ZSET의 Top-N을 상품 정보로 풍부화하여 반환한다")
        void enrichesEntriesWithProductInfo() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            when(rankingRepository.findTopN(eq(date), eq(0L), eq(2L))).thenReturn(List.of(
                    new RankingEntry(1L, 100.0),
                    new RankingEntry(2L, 80.0)
            ));
            when(rankingRepository.countMembers(date)).thenReturn(2L);
            when(productCache.findById(1L)).thenReturn(new CachedProductSnapshot(
                    1L, "P001", "상품 A", new BigDecimal("1000"), false, Instant.now().getEpochSecond()));
            when(productCache.findById(2L)).thenReturn(new CachedProductSnapshot(
                    2L, "P002", "상품 B", new BigDecimal("2000"), false, Instant.now().getEpochSecond()));

            RankingPageResult result = rankingApp.getTopN(date, 0, 2);

            assertThat(result.items()).hasSize(2);
            assertThat(result.items().get(0).rank()).isEqualTo(1L);
            assertThat(result.items().get(0).score()).isEqualTo(100.0);
            assertThat(result.items().get(0).productName()).isEqualTo("상품 A");
            assertThat(result.items().get(0).status()).isEqualTo(RankingInfo.STATUS_ACTIVE);
            assertThat(result.items().get(1).rank()).isEqualTo(2L);
            assertThat(result.totalElements()).isEqualTo(2L);
        }

        @Test
        @DisplayName("삭제된 상품은 랭킹에서 숨긴다 (멘토링 피드백: 부정적 피드백 미노출)")
        void deletedProductIsHidden() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            when(rankingRepository.findTopN(eq(date), any(Long.class), any(Long.class))).thenReturn(List.of(
                    new RankingEntry(10L, 50.0),
                    new RankingEntry(11L, 40.0)
            ));
            when(rankingRepository.countMembers(date)).thenReturn(2L);
            when(productCache.findById(10L)).thenReturn(new CachedProductSnapshot(
                    10L, "P010", "판매종료 상품", new BigDecimal("500"), true, Instant.now().getEpochSecond()));
            when(productCache.findById(11L)).thenReturn(new CachedProductSnapshot(
                    11L, "P011", "정상 상품", new BigDecimal("300"), false, Instant.now().getEpochSecond()));

            RankingPageResult result = rankingApp.getTopN(date, 0, 10);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).productName()).isEqualTo("정상 상품");
            assertThat(result.items().get(0).status()).isEqualTo(RankingInfo.STATUS_ACTIVE);
        }

        @Test
        @DisplayName("캐시에 없는 상품(DB에서도 삭제)도 랭킹에서 숨긴다")
        void missingProductIsHidden() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            when(rankingRepository.findTopN(eq(date), any(Long.class), any(Long.class))).thenReturn(List.of(
                    new RankingEntry(999L, 30.0)
            ));
            when(rankingRepository.countMembers(date)).thenReturn(1L);
            when(productCache.findById(999L)).thenReturn(null);

            RankingPageResult result = rankingApp.getTopN(date, 0, 10);

            assertThat(result.items()).isEmpty();
        }

        @Test
        @DisplayName("빈 랭킹은 empty items로 반환한다 (200 OK)")
        void emptyRankingReturnsEmptyItems() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            when(rankingRepository.findTopN(eq(date), any(Long.class), any(Long.class))).thenReturn(List.of());
            when(rankingRepository.countMembers(date)).thenReturn(0L);

            RankingPageResult result = rankingApp.getTopN(date, 0, 10);

            assertThat(result.items()).isEmpty();
            assertThat(result.totalElements()).isZero();
        }

        @Test
        @DisplayName("2페이지(page=1, size=10) 조회 시 rank은 11부터 시작한다")
        void secondPageRankStartsFromEleven() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            when(rankingRepository.findTopN(eq(date), eq(10L), eq(10L))).thenReturn(List.of(
                    new RankingEntry(11L, 5.0)
            ));
            when(rankingRepository.countMembers(date)).thenReturn(20L);
            when(productCache.findById(11L)).thenReturn(new CachedProductSnapshot(
                    11L, "P011", "상품 11", new BigDecimal("100"), false, Instant.now().getEpochSecond()));

            RankingPageResult result = rankingApp.getTopN(date, 1, 10);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).rank()).isEqualTo(11L);
        }
    }

    @Nested
    @DisplayName("getProductRanking()")
    class GetProductRanking {

        @Test
        @DisplayName("ZSET에 없는 상품은 Optional.empty()를 반환한다 (랭킹 미참여)")
        void productNotInZsetReturnsEmpty() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            when(rankingRepository.findRank(date, 999L)).thenReturn(Optional.empty());

            Optional<ProductRankingInfo> result = rankingApp.getProductRanking(999L, date);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("ZSET에 있는 상품은 rank + score를 반환한다")
        void productInZsetReturnsRankAndScore() {
            LocalDate date = LocalDate.of(2026, 4, 5);
            when(rankingRepository.findRank(date, 42L)).thenReturn(Optional.of(3L));
            when(rankingRepository.findScore(date, 42L)).thenReturn(Optional.of(77.7));

            Optional<ProductRankingInfo> result = rankingApp.getProductRanking(42L, date);

            assertThat(result).isPresent();
            assertThat(result.get().rank()).isEqualTo(3L);
            assertThat(result.get().score()).isEqualTo(77.7);
        }
    }
}

package com.loopers.application.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingEntry;
import com.loopers.domain.ranking.RankingRepository;
import com.loopers.domain.ranking.mv.MvRankingRepository;
import com.loopers.infrastructure.ranking.RankingCacheStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RankingFacadeTest {

    private RankingRepository rankingRepository;
    private ProductRepository productRepository;
    private BrandRepository brandRepository;
    private RankingCacheStore rankingCacheStore;
    private MvRankingRepository mvRankingRepository;
    private RankingFacade rankingFacade;

    private final String todayKey = "ranking:all:" +
        LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ofPattern("yyyyMMdd"));

    @BeforeEach
    void setUp() {
        rankingRepository = mock(RankingRepository.class);
        productRepository = mock(ProductRepository.class);
        brandRepository = mock(BrandRepository.class);
        rankingCacheStore = mock(RankingCacheStore.class);
        mvRankingRepository = mock(MvRankingRepository.class);
        rankingFacade = new RankingFacade(rankingRepository, productRepository, brandRepository, rankingCacheStore, mvRankingRepository);

        when(rankingCacheStore.getRankings(anyString(), anyString(), anyInt(), anyInt())).thenReturn(Optional.empty());
    }

    @DisplayName("랭킹 페이지 조회 시 상품 정보가 포함된 랭킹 목록을 반환한다")
    @Test
    void getRankings_returnsRankingWithProductInfo() {
        String date = "20260405";
        String key = "ranking:all:" + date;
        RankingEntry entry1 = new RankingEntry(10L, 5.0);
        RankingEntry entry2 = new RankingEntry(20L, 3.0);
        when(rankingRepository.getTopRankings(eq(key), eq(0L), anyLong())).thenReturn(List.of(entry1, entry2));

        Product product1 = Product.create(1L, "상품A", "설명A", BigDecimal.valueOf(10000), 100);
        Product product2 = Product.create(1L, "상품B", "설명B", BigDecimal.valueOf(20000), 50);
        setId(product1, 10L);
        setId(product2, 20L);
        when(productRepository.findAllByIds(anyCollection())).thenReturn(List.of(product1, product2));

        Brand brand = Brand.create(new BrandName("브랜드"), new BrandDescription("설명"));
        setId(brand, 1L);
        when(brandRepository.findAllByIds(anyCollection())).thenReturn(List.of(brand));

        List<RankingDto.RankingItemInfo> result = rankingFacade.getRankings("daily", date, 0, 20);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).rank()).isEqualTo(1);
        assertThat(result.get(0).score()).isEqualTo(5.0);
        assertThat(result.get(0).productName()).isEqualTo("상품A");
        assertThat(result.get(1).rank()).isEqualTo(2);
        assertThat(result.get(1).productName()).isEqualTo("상품B");
    }

    @DisplayName("랭킹 데이터가 없으면 빈 목록을 반환한다")
    @Test
    void getRankings_emptyZset_returnsEmptyList() {
        when(rankingRepository.getTopRankings(anyString(), anyLong(), anyLong())).thenReturn(List.of());

        List<RankingDto.RankingItemInfo> result = rankingFacade.getRankings("daily", "20260405", 0, 20);

        assertThat(result).isEmpty();
    }

    @DisplayName("상품 랭킹 조회 시 순위와 점수를 반환한다")
    @Test
    void getProductRank_returnsRankAndScore() {
        String todayDate = rankingFacade.todayDate();
        when(rankingRepository.getRank(todayKey, 10L)).thenReturn(0L);
        when(rankingRepository.getScore(todayKey, 10L)).thenReturn(5.0);

        RankingDto.ProductRankInfo result = rankingFacade.getProductRank(10L, todayDate);

        assertThat(result.rank()).isEqualTo(1);
        assertThat(result.score()).isEqualTo(5.0);
    }

    @DisplayName("랭킹에 없는 상품은 null을 반환한다")
    @Test
    void getProductRank_notInRanking_returnsNull() {
        String todayDate = rankingFacade.todayDate();
        when(rankingRepository.getRank(todayKey, 999L)).thenReturn(null);

        RankingDto.ProductRankInfo result = rankingFacade.getProductRank(999L, todayDate);

        assertThat(result.rank()).isNull();
        assertThat(result.score()).isNull();
    }

    @DisplayName("2페이지 조회 시 start가 올바르게 계산된다")
    @Test
    void getRankings_page1_calculatesCorrectStart() {
        String date = "20260405";
        String key = "ranking:all:" + date;
        when(rankingRepository.getTopRankings(eq(key), eq(20L), anyLong())).thenReturn(List.of());

        rankingFacade.getRankings("daily", date, 1, 20);

        verify(rankingRepository).getTopRankings(eq(key), eq(20L), anyLong());
    }

    @DisplayName("캐시 적중 시 ZSET과 DB를 조회하지 않는다")
    @Test
    void getRankings_cacheHit_skipsZsetAndDb() {
        List<RankingDto.RankingItemInfo> cached = List.of(
            new RankingDto.RankingItemInfo(1, 5.0, 10L, "상품A", "브랜드", BigDecimal.valueOf(10000))
        );
        when(rankingCacheStore.getRankings("daily", "20260405", 0, 20)).thenReturn(Optional.of(cached));

        List<RankingDto.RankingItemInfo> result = rankingFacade.getRankings("daily", "20260405", 0, 20);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).productName()).isEqualTo("상품A");
        verify(rankingRepository, never()).getTopRankings(anyString(), anyLong(), anyLong());
        verify(productRepository, never()).findAllByIds(anyCollection());
    }

    @DisplayName("캐시 미스 시 조회 결과를 캐시에 저장한다")
    @Test
    void getRankings_cacheMiss_savesToCache() {
        String date = "20260405";
        RankingEntry entry = new RankingEntry(10L, 5.0);
        when(rankingRepository.getTopRankings(eq("ranking:all:" + date), eq(0L), anyLong())).thenReturn(List.of(entry));

        Product product = Product.create(1L, "상품A", "설명A", BigDecimal.valueOf(10000), 100);
        setId(product, 10L);
        when(productRepository.findAllByIds(anyCollection())).thenReturn(List.of(product));

        Brand brand = Brand.create(new BrandName("브랜드"), new BrandDescription("설명"));
        setId(brand, 1L);
        when(brandRepository.findAllByIds(anyCollection())).thenReturn(List.of(brand));

        rankingFacade.getRankings("daily", date, 0, 20);

        verify(rankingCacheStore).putRankings(eq("daily"), eq(date), eq(0), eq(20), anyList());
    }

    private void setId(Object entity, Long id) {
        try {
            Class<?> clazz = entity.getClass();
            while (clazz != null) {
                try {
                    var field = clazz.getDeclaredField("id");
                    field.setAccessible(true);
                    field.set(entity, id);
                    return;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }
            throw new NoSuchFieldException("id field not found in class hierarchy");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

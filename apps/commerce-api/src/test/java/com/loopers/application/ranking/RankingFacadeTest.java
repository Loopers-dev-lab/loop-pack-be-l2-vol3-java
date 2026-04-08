package com.loopers.application.ranking;

import com.loopers.config.RankingProperties;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.domain.common.vo.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductStatus;
import com.loopers.infrastructure.ranking.RankingRedisRepository;
import com.loopers.infrastructure.ranking.RankingRedisRepository.RankingEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class RankingFacadeTest {

    private RankingRedisRepository rankingRedisRepository;
    private ProductService productService;
    private BrandService brandService;
    private RankingProperties rankingProperties;
    private RankingFacade rankingFacade;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final String TODAY = LocalDate.now(KST)
            .format(DateTimeFormatter.BASIC_ISO_DATE);
    private static final String TODAY_KEY = "ranking:all:" + TODAY;
    private static final String CURRENT_HOUR = LocalDateTime.now(KST)
            .format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
    private static final String HOURLY_KEY = "ranking:hourly:" + CURRENT_HOUR;

    @BeforeEach
    void setUp() {
        rankingRedisRepository = Mockito.mock(RankingRedisRepository.class);
        productService = Mockito.mock(ProductService.class);
        brandService = Mockito.mock(BrandService.class);

        rankingProperties = new RankingProperties();
        rankingProperties.setKeyPrefix("ranking:all");
        rankingProperties.setTtlDays(2);
        rankingProperties.setHourlyKeyPrefix("ranking:hourly");
        rankingProperties.setHourlyTtlHours(4);

        rankingFacade = new RankingFacade(
                rankingRedisRepository, productService, brandService, rankingProperties);
    }

    @DisplayName("일간 랭킹 페이지 조회")
    @Nested
    class 일간_랭킹_페이지_조회 {

        @Test
        void ZSET에_데이터가_있으면_상품정보와_함께_반환한다() {
            // arrange
            when(rankingRedisRepository.getSize(TODAY_KEY)).thenReturn(2L);
            when(rankingRedisRepository.getTopWithScores(eq(TODAY_KEY), eq(0L), eq(19L)))
                    .thenReturn(List.of(
                            new RankingEntry(100L, 5.6),
                            new RankingEntry(200L, 3.2)
                    ));

            Product product1 = createProduct(100L, 1L, "상품A", ProductStatus.ACTIVE);
            Product product2 = createProduct(200L, 1L, "상품B", ProductStatus.ACTIVE);
            when(productService.getProductsByIds(List.of(100L, 200L)))
                    .thenReturn(List.of(product1, product2));

            Brand brand = createBrand(1L, "브랜드");
            when(brandService.getBrandsByIds(anyList())).thenReturn(List.of(brand));

            // act
            RankingFacade.RankingPageResult result = rankingFacade.getRankings("daily", null, 1, 20);

            // assert
            assertThat(result.items()).hasSize(2);
            assertThat(result.items().get(0).rank()).isEqualTo(1);
            assertThat(result.items().get(0).product().name()).isEqualTo("상품A");
            assertThat(result.items().get(0).score()).isEqualTo(5.6);
            assertThat(result.items().get(1).rank()).isEqualTo(2);
            assertThat(result.totalCount()).isEqualTo(2);
        }

        @Test
        void ZSET이_비어있으면_빈_결과를_반환한다() {
            when(rankingRedisRepository.getSize(any())).thenReturn(0L);

            RankingFacade.RankingPageResult result = rankingFacade.getRankings("daily", null, 1, 20);

            assertThat(result.items()).isEmpty();
            assertThat(result.totalCount()).isEqualTo(0);
            assertThat(result.hasNext()).isFalse();
        }

        @Test
        void 비활성_상품은_응답에서_제외된다() {
            when(rankingRedisRepository.getSize(TODAY_KEY)).thenReturn(2L);
            when(rankingRedisRepository.getTopWithScores(eq(TODAY_KEY), eq(0L), eq(19L)))
                    .thenReturn(List.of(
                            new RankingEntry(100L, 5.0),
                            new RankingEntry(200L, 3.0)
                    ));

            Product activeProduct = createProduct(100L, 1L, "활성상품", ProductStatus.ACTIVE);
            Product hiddenProduct = createProduct(200L, 1L, "숨김상품", ProductStatus.HIDDEN);
            when(productService.getProductsByIds(anyList()))
                    .thenReturn(List.of(activeProduct, hiddenProduct));

            Brand brand = createBrand(1L, "브랜드");
            when(brandService.getBrandsByIds(anyList())).thenReturn(List.of(brand));

            RankingFacade.RankingPageResult result = rankingFacade.getRankings("daily", null, 1, 20);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).product().name()).isEqualTo("활성상품");
        }

        @Test
        void 날짜를_지정하면_해당_날짜의_ZSET을_조회한다() {
            String specificDate = "20260401";
            String specificKey = "ranking:all:" + specificDate;

            when(rankingRedisRepository.getSize(specificKey)).thenReturn(1L);
            when(rankingRedisRepository.getTopWithScores(eq(specificKey), eq(0L), eq(19L)))
                    .thenReturn(List.of(new RankingEntry(100L, 2.0)));

            Product product = createProduct(100L, 1L, "상품", ProductStatus.ACTIVE);
            when(productService.getProductsByIds(anyList())).thenReturn(List.of(product));

            Brand brand = createBrand(1L, "브랜드");
            when(brandService.getBrandsByIds(anyList())).thenReturn(List.of(brand));

            RankingFacade.RankingPageResult result = rankingFacade.getRankings("daily", specificDate, 1, 20);

            assertThat(result.items()).hasSize(1);
        }

        @Test
        void period_미지정시_daily가_기본이다() {
            when(rankingRedisRepository.getSize(TODAY_KEY)).thenReturn(1L);
            when(rankingRedisRepository.getTopWithScores(eq(TODAY_KEY), eq(0L), eq(19L)))
                    .thenReturn(List.of(new RankingEntry(100L, 1.0)));

            Product product = createProduct(100L, 1L, "상품", ProductStatus.ACTIVE);
            when(productService.getProductsByIds(anyList())).thenReturn(List.of(product));

            Brand brand = createBrand(1L, "브랜드");
            when(brandService.getBrandsByIds(anyList())).thenReturn(List.of(brand));

            // period = null → daily
            RankingFacade.RankingPageResult result = rankingFacade.getRankings(null, null, 1, 20);

            assertThat(result.items()).hasSize(1);
        }
    }

    @DisplayName("시간 단위 랭킹 조회")
    @Nested
    class 시간_단위_랭킹_조회 {

        @Test
        void hourly_period로_현재_시간_키를_조회한다() {
            when(rankingRedisRepository.getSize(HOURLY_KEY)).thenReturn(1L);
            when(rankingRedisRepository.getTopWithScores(eq(HOURLY_KEY), eq(0L), eq(19L)))
                    .thenReturn(List.of(new RankingEntry(100L, 3.0)));

            Product product = createProduct(100L, 1L, "지금뜨는상품", ProductStatus.ACTIVE);
            when(productService.getProductsByIds(anyList())).thenReturn(List.of(product));

            Brand brand = createBrand(1L, "브랜드");
            when(brandService.getBrandsByIds(anyList())).thenReturn(List.of(brand));

            RankingFacade.RankingPageResult result = rankingFacade.getRankings("hourly", null, 1, 20);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).product().name()).isEqualTo("지금뜨는상품");
        }

        @Test
        void hourly에서_특정_시간을_지정하면_해당_키를_조회한다() {
            String specificHour = "2026040814";
            String specificKey = "ranking:hourly:" + specificHour;

            when(rankingRedisRepository.getSize(specificKey)).thenReturn(1L);
            when(rankingRedisRepository.getTopWithScores(eq(specificKey), eq(0L), eq(19L)))
                    .thenReturn(List.of(new RankingEntry(100L, 2.0)));

            Product product = createProduct(100L, 1L, "14시인기상품", ProductStatus.ACTIVE);
            when(productService.getProductsByIds(anyList())).thenReturn(List.of(product));

            Brand brand = createBrand(1L, "브랜드");
            when(brandService.getBrandsByIds(anyList())).thenReturn(List.of(brand));

            RankingFacade.RankingPageResult result = rankingFacade.getRankings("hourly", specificHour, 1, 20);

            assertThat(result.items()).hasSize(1);
        }

        @Test
        void hourly에서_현재_키가_비어있으면_직전_시간으로_fallback한다() {
            String prevHour = LocalDateTime.now(KST).minusHours(1)
                    .format(DateTimeFormatter.ofPattern("yyyyMMddHH"));
            String prevHourKey = "ranking:hourly:" + prevHour;

            when(rankingRedisRepository.getSize(HOURLY_KEY)).thenReturn(0L);
            when(rankingRedisRepository.getSize(prevHourKey)).thenReturn(1L);
            when(rankingRedisRepository.getTopWithScores(eq(prevHourKey), eq(0L), eq(19L)))
                    .thenReturn(List.of(new RankingEntry(100L, 1.5)));

            Product product = createProduct(100L, 1L, "직전시간인기상품", ProductStatus.ACTIVE);
            when(productService.getProductsByIds(anyList())).thenReturn(List.of(product));

            Brand brand = createBrand(1L, "브랜드");
            when(brandService.getBrandsByIds(anyList())).thenReturn(List.of(brand));

            RankingFacade.RankingPageResult result = rankingFacade.getRankings("hourly", null, 1, 20);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).product().name()).isEqualTo("직전시간인기상품");
        }
    }

    @DisplayName("콜드 스타트 fallback — 일간")
    @Nested
    class 콜드_스타트_fallback_일간 {

        @Test
        void 오늘_키가_비어있으면_어제_키로_fallback한다() {
            String yesterdayKey = "ranking:all:" + LocalDate.now(KST)
                    .minusDays(1).format(DateTimeFormatter.BASIC_ISO_DATE);

            when(rankingRedisRepository.getSize(TODAY_KEY)).thenReturn(0L);
            when(rankingRedisRepository.getSize(yesterdayKey)).thenReturn(1L);
            when(rankingRedisRepository.getTopWithScores(eq(yesterdayKey), eq(0L), eq(19L)))
                    .thenReturn(List.of(new RankingEntry(100L, 1.0)));

            Product product = createProduct(100L, 1L, "어제인기상품", ProductStatus.ACTIVE);
            when(productService.getProductsByIds(anyList())).thenReturn(List.of(product));

            Brand brand = createBrand(1L, "브랜드");
            when(brandService.getBrandsByIds(anyList())).thenReturn(List.of(brand));

            RankingFacade.RankingPageResult result = rankingFacade.getRankings("daily", null, 1, 20);

            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).product().name()).isEqualTo("어제인기상품");
        }

        @Test
        void 날짜를_명시적으로_지정하면_fallback하지_않는다() {
            when(rankingRedisRepository.getSize("ranking:all:" + TODAY)).thenReturn(0L);

            RankingFacade.RankingPageResult result = rankingFacade.getRankings("daily", TODAY, 1, 20);

            assertThat(result.items()).isEmpty();
        }
    }

    @DisplayName("상품 랭킹 조회")
    @Nested
    class 상품_랭킹_조회 {

        @Test
        void ZSET에_있는_상품은_1_based_순위를_반환한다() {
            when(rankingRedisRepository.getRank(TODAY_KEY, 100L)).thenReturn(0L);
            when(rankingRedisRepository.getScore(TODAY_KEY, 100L)).thenReturn(5.6);

            RankingFacade.ProductRankInfo rankInfo = rankingFacade.getProductRank(100L);

            assertThat(rankInfo).isNotNull();
            assertThat(rankInfo.rank()).isEqualTo(1);
            assertThat(rankInfo.score()).isEqualTo(5.6);
        }

        @Test
        void ZSET에_없는_상품은_null을_반환한다() {
            when(rankingRedisRepository.getRank(TODAY_KEY, 999L)).thenReturn(null);

            RankingFacade.ProductRankInfo rankInfo = rankingFacade.getProductRank(999L);

            assertThat(rankInfo).isNull();
        }
    }

    // --- Test Helpers ---

    private Product createProduct(Long id, Long brandId, String name, ProductStatus status) {
        return Product.reconstitute(
                id, brandId, name, "설명", new Money(10000), status, 0,
                java.time.ZonedDateTime.now(), java.time.ZonedDateTime.now(), null);
    }

    private Brand createBrand(Long id, String name) {
        return Brand.reconstitute(
                id, name, "설명", BrandStatus.ACTIVE,
                java.time.ZonedDateTime.now(), java.time.ZonedDateTime.now(), null);
    }
}

package com.loopers.domain.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.support.error.CoreException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RankingQueryServiceTest {

    @Mock
    private RankingReadRepository rankingReadRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private BrandService brandService;

    @Mock
    private LikeService likeService;

    private RankingQueryService rankingQueryService;

    @BeforeEach
    void setUp() {
        rankingQueryService = new RankingQueryService(
                rankingReadRepository,
                productRepository,
                brandService,
                likeService
        );
    }

    @Test
    @DisplayName("ZSET 순서대로 rank·score·상품 정보를 조합한다.")
    void loadPage_whenTwoProducts_shouldReturnOrderedRows() {
        LocalDate date = LocalDate.of(2026, 3, 26);
        when(rankingReadRepository.count("ranking:all:20260326")).thenReturn(2L);
        when(rankingReadRepository.findReverseRangeWithScores("ranking:all:20260326", 0L, 1L))
                .thenReturn(List.of(
                        new RankingZsetEntry("101", 0.5d),
                        new RankingZsetEntry("102", 0.3d)
                ));

        ProductModel p101 = mock(ProductModel.class);
        when(p101.getBrandId()).thenReturn(1L);
        when(p101.getName()).thenReturn("A");
        when(p101.getPrice()).thenReturn(new BigDecimal("1000"));

        ProductModel p102 = mock(ProductModel.class);
        when(p102.getBrandId()).thenReturn(1L);
        when(p102.getName()).thenReturn("B");
        when(p102.getPrice()).thenReturn(new BigDecimal("2000"));

        when(productRepository.findByIdInAndNotDeletedAsMap(anyCollection()))
                .thenReturn(Map.of(101L, p101, 102L, p102));

        BrandModel brand = mock(BrandModel.class);
        when(brand.getName()).thenReturn("브랜드");
        when(brandService.findByIdAndNotDeletedIn(anyCollection())).thenReturn(Map.of(1L, brand));
        when(likeService.getLikeCountByProductIdsFromStats(anyCollection()))
                .thenReturn(Map.of(101L, 3L, 102L, 5L));

        RankingPage result = rankingQueryService.loadPage(date, 1, 10);

        assertThat(result.totalElements()).isEqualTo(2L);
        assertThat(result.totalPages()).isEqualTo(1);
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).rank()).isEqualTo(1);
        assertThat(result.rows().get(0).productId()).isEqualTo(101L);
        assertThat(result.rows().get(0).score()).isEqualTo(0.5d);
        assertThat(result.rows().get(0).likeCount()).isEqualTo(3L);
        assertThat(result.rows().get(1).rank()).isEqualTo(2);
        assertThat(result.rows().get(1).productId()).isEqualTo(102L);
    }

    @Test
    @DisplayName("ZSET은 있으나 DB에 없는 상품은 응답에서 제외한다.")
    void loadPage_whenProductMissingInDb_shouldSkipRow() {
        LocalDate date = LocalDate.of(2026, 3, 26);
        when(rankingReadRepository.count("ranking:all:20260326")).thenReturn(1L);
        when(rankingReadRepository.findReverseRangeWithScores("ranking:all:20260326", 0L, 0L))
                .thenReturn(List.of(new RankingZsetEntry("999", 1.0d)));
        when(productRepository.findByIdInAndNotDeletedAsMap(anyCollection())).thenReturn(Map.of());

        RankingPage result = rankingQueryService.loadPage(date, 1, 10);

        assertThat(result.rows()).isEmpty();
    }

    @Test
    @DisplayName("키가 비어 있으면 빈 목록과 total 0을 반환한다.")
    void loadPage_whenEmptyZset_shouldReturnEmpty() {
        when(rankingReadRepository.count("ranking:all:20260326")).thenReturn(0L);

        RankingPage result = rankingQueryService.loadPage(LocalDate.of(2026, 3, 26), 1, 20);

        assertThat(result.totalElements()).isZero();
        assertThat(result.rows()).isEmpty();
    }

    @Test
    @DisplayName("findOneBasedDailyRank: ZSET에 있으면 점수 내림차순 1-based 순위를 반환한다.")
    void findOneBasedDailyRank_whenMemberExists_shouldReturnOneBasedRank() {
        LocalDate date = LocalDate.of(2026, 3, 26);
        when(rankingReadRepository.findOneBasedReverseRank(eq("ranking:all:20260326"), eq("101")))
                .thenReturn(OptionalLong.of(2L));

        OptionalLong result = rankingQueryService.findOneBasedDailyRank(date, 101L);

        assertThat(result).isPresent();
        assertThat(result.getAsLong()).isEqualTo(2L);
    }

    @Test
    @DisplayName("findOneBasedDailyRank: ZSET에 없으면 empty를 반환한다.")
    void findOneBasedDailyRank_whenMemberMissing_shouldReturnEmpty() {
        LocalDate date = LocalDate.of(2026, 3, 26);
        when(rankingReadRepository.findOneBasedReverseRank("ranking:all:20260326", "999"))
                .thenReturn(OptionalLong.empty());

        OptionalLong result = rankingQueryService.findOneBasedDailyRank(date, 999L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findOneBasedDailyRank: productId가 0 이하면 BAD_REQUEST")
    void findOneBasedDailyRank_whenProductIdInvalid_shouldThrow() {
        assertThatThrownBy(() -> rankingQueryService.findOneBasedDailyRank(LocalDate.of(2026, 3, 26), 0L))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("page=2일 때 ZSET 오프셋(start=size) 구간을 조회한다")
    void loadPage_whenPageTwo_shouldQuerySliceFromOffset() {
        LocalDate date = LocalDate.of(2026, 3, 26);
        when(rankingReadRepository.count("ranking:all:20260326")).thenReturn(5L);
        when(rankingReadRepository.findReverseRangeWithScores("ranking:all:20260326", 2L, 3L))
                .thenReturn(List.of(
                        new RankingZsetEntry("103", 0.7d),
                        new RankingZsetEntry("104", 0.6d)
                ));

        ProductModel p103 = mock(ProductModel.class);
        when(p103.getBrandId()).thenReturn(1L);
        when(p103.getName()).thenReturn("C");
        when(p103.getPrice()).thenReturn(new BigDecimal("3000"));
        ProductModel p104 = mock(ProductModel.class);
        when(p104.getBrandId()).thenReturn(1L);
        when(p104.getName()).thenReturn("D");
        when(p104.getPrice()).thenReturn(new BigDecimal("4000"));

        when(productRepository.findByIdInAndNotDeletedAsMap(anyCollection()))
                .thenReturn(Map.of(103L, p103, 104L, p104));
        BrandModel brand = mock(BrandModel.class);
        when(brand.getName()).thenReturn("B");
        when(brandService.findByIdAndNotDeletedIn(anyCollection())).thenReturn(Map.of(1L, brand));
        when(likeService.getLikeCountByProductIdsFromStats(anyCollection())).thenReturn(Map.of());

        RankingPage result = rankingQueryService.loadPage(date, 2, 2);

        assertThat(result.totalElements()).isEqualTo(5L);
        assertThat(result.totalPages()).isEqualTo(3);
        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).rank()).isEqualTo(3);
        assertThat(result.rows().get(0).productId()).isEqualTo(103L);
        assertThat(result.rows().get(1).rank()).isEqualTo(4);
        assertThat(result.rows().get(1).productId()).isEqualTo(104L);
    }

    @Test
    @DisplayName("요청 page가 총 페이지를 넘기면 빈 content·total은 유지한다 (오프셋 초과).")
    void loadPage_whenPageBeyondLast_shouldReturnEmptyContentWithTotals() {
        LocalDate date = LocalDate.of(2026, 3, 26);
        when(rankingReadRepository.count("ranking:all:20260326")).thenReturn(3L);

        RankingPage result = rankingQueryService.loadPage(date, 3, 2);

        assertThat(result.totalElements()).isEqualTo(3L);
        assertThat(result.totalPages()).isEqualTo(2);
        assertThat(result.rows()).isEmpty();
        assertThat(result.page()).isEqualTo(3);
    }

    @Test
    @DisplayName("동일 score 구간은 Redis가 반환한 member 순서를 그대로 쓴다 (동점 시 순서는 저장소 규칙에 따름)")
    void loadPage_whenTieScore_shouldKeepRepositoryOrder() {
        LocalDate date = LocalDate.of(2026, 3, 26);
        when(rankingReadRepository.count("ranking:all:20260326")).thenReturn(2L);
        when(rankingReadRepository.findReverseRangeWithScores("ranking:all:20260326", 0L, 1L))
                .thenReturn(List.of(
                        new RankingZsetEntry("102", 0.5d),
                        new RankingZsetEntry("101", 0.5d)
                ));

        ProductModel p101 = mock(ProductModel.class);
        when(p101.getBrandId()).thenReturn(1L);
        when(p101.getName()).thenReturn("A");
        when(p101.getPrice()).thenReturn(new BigDecimal("1000"));
        ProductModel p102 = mock(ProductModel.class);
        when(p102.getBrandId()).thenReturn(1L);
        when(p102.getName()).thenReturn("B");
        when(p102.getPrice()).thenReturn(new BigDecimal("2000"));

        when(productRepository.findByIdInAndNotDeletedAsMap(anyCollection()))
                .thenReturn(Map.of(101L, p101, 102L, p102));
        BrandModel brand = mock(BrandModel.class);
        when(brand.getName()).thenReturn("브랜드");
        when(brandService.findByIdAndNotDeletedIn(anyCollection())).thenReturn(Map.of(1L, brand));
        when(likeService.getLikeCountByProductIdsFromStats(anyCollection())).thenReturn(Map.of());

        RankingPage result = rankingQueryService.loadPage(date, 1, 10);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.rows().get(0).rank()).isEqualTo(1);
        assertThat(result.rows().get(0).productId()).isEqualTo(102L);
        assertThat(result.rows().get(1).rank()).isEqualTo(2);
        assertThat(result.rows().get(1).productId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("Redis 장애 시 랭킹 목록은 빈 결과로 성능저하 모드 응답한다.")
    void loadPage_whenRedisUnavailable_shouldReturnEmptyDegradedPage() {
        when(rankingReadRepository.count("ranking:all:20260326"))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        RankingPage result = rankingQueryService.loadPage(LocalDate.of(2026, 3, 26), 1, 20);

        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.rows()).isEmpty();
    }

    @Test
    @DisplayName("Redis 장애 시 상품 상세 순위는 null 매핑 가능한 empty를 반환한다.")
    void findOneBasedDailyRank_whenRedisUnavailable_shouldReturnEmpty() {
        when(rankingReadRepository.findOneBasedReverseRank("ranking:all:20260326", "101"))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        OptionalLong result = rankingQueryService.findOneBasedDailyRank(LocalDate.of(2026, 3, 26), 101L);

        assertThat(result).isEmpty();
    }
}

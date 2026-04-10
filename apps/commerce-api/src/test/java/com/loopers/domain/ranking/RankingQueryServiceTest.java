package com.loopers.domain.ranking;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSortOrder;
import com.loopers.support.error.CoreException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.RedisSystemException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
        rankingQueryService = newService(false);
    }

    private RankingQueryService newService(boolean fallbackOnRedisFailure) {
        return new RankingQueryService(
                rankingReadRepository,
                productRepository,
                brandService,
                likeService,
                new SimpleMeterRegistry(),
                fallbackOnRedisFailure
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
        when(p101.getStockQuantity()).thenReturn(3);

        ProductModel p102 = mock(ProductModel.class);
        when(p102.getBrandId()).thenReturn(1L);
        when(p102.getName()).thenReturn("B");
        when(p102.getPrice()).thenReturn(new BigDecimal("2000"));
        when(p102.getStockQuantity()).thenReturn(0);

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
        assertThat(result.listSource()).isEqualTo(RankingListSource.REDIS_ZSET);
        assertThat(result.rows().get(0).rank()).isEqualTo(1);
        assertThat(result.rows().get(0).productId()).isEqualTo(101L);
        assertThat(result.rows().get(0).score()).isEqualTo(0.5d);
        assertThat(result.rows().get(0).likeCount()).isEqualTo(3L);
        assertThat(result.rows().get(0).stockQuantity()).isEqualTo(3);
        assertThat(result.rows().get(1).rank()).isEqualTo(2);
        assertThat(result.rows().get(1).productId()).isEqualTo(102L);
        assertThat(result.rows().get(1).stockQuantity()).isEqualTo(0);
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
        assertThat(result.listSource()).isEqualTo(RankingListSource.REDIS_ZSET);
    }

    @Test
    @DisplayName("키가 비어 있으면 빈 목록과 total 0을 반환한다.")
    void loadPage_whenEmptyZset_shouldReturnEmpty() {
        when(rankingReadRepository.count("ranking:all:20260326")).thenReturn(0L);

        RankingPage result = rankingQueryService.loadPage(LocalDate.of(2026, 3, 26), 1, 20);

        assertThat(result.totalElements()).isZero();
        assertThat(result.rows()).isEmpty();
        assertThat(result.listSource()).isEqualTo(RankingListSource.REDIS_ZSET);
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
        when(p103.getStockQuantity()).thenReturn(1);
        ProductModel p104 = mock(ProductModel.class);
        when(p104.getBrandId()).thenReturn(1L);
        when(p104.getName()).thenReturn("D");
        when(p104.getPrice()).thenReturn(new BigDecimal("4000"));
        when(p104.getStockQuantity()).thenReturn(2);

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
        assertThat(result.listSource()).isEqualTo(RankingListSource.REDIS_ZSET);
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
        assertThat(result.listSource()).isEqualTo(RankingListSource.REDIS_ZSET);
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
        when(p101.getStockQuantity()).thenReturn(0);
        ProductModel p102 = mock(ProductModel.class);
        when(p102.getBrandId()).thenReturn(1L);
        when(p102.getName()).thenReturn("B");
        when(p102.getPrice()).thenReturn(new BigDecimal("2000"));
        when(p102.getStockQuantity()).thenReturn(0);

        when(productRepository.findByIdInAndNotDeletedAsMap(anyCollection()))
                .thenReturn(Map.of(101L, p101, 102L, p102));
        BrandModel brand = mock(BrandModel.class);
        when(brand.getName()).thenReturn("브랜드");
        when(brandService.findByIdAndNotDeletedIn(anyCollection())).thenReturn(Map.of(1L, brand));
        when(likeService.getLikeCountByProductIdsFromStats(anyCollection())).thenReturn(Map.of());

        RankingPage result = rankingQueryService.loadPage(date, 1, 10);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.listSource()).isEqualTo(RankingListSource.REDIS_ZSET);
        assertThat(result.rows().get(0).rank()).isEqualTo(1);
        assertThat(result.rows().get(0).productId()).isEqualTo(102L);
        assertThat(result.rows().get(1).rank()).isEqualTo(2);
        assertThat(result.rows().get(1).productId()).isEqualTo(101L);
    }

    @Test
    @DisplayName("Redis 장애 시 fallback 비활성화면 빈 목록·DEGRADED_EMPTY")
    void loadPage_whenRedisUnavailable_andFallbackOff_shouldReturnEmptyDegradedPage() {
        RankingQueryService svc = newService(false);
        when(rankingReadRepository.count("ranking:all:20260326"))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        RankingPage result = svc.loadPage(LocalDate.of(2026, 3, 26), 1, 20);

        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.rows()).isEmpty();
        assertThat(result.listSource()).isEqualTo(RankingListSource.DEGRADED_EMPTY);
    }

    @Test
    @DisplayName("Redis 장애 시 fallback 활성화면 DB 최신순으로 채운다.")
    void loadPage_whenRedisUnavailable_andFallbackOn_shouldReturnLatestFromDb() {
        RankingQueryService svc = newService(true);
        when(rankingReadRepository.count("ranking:all:20260326"))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        ProductModel p = mock(ProductModel.class);
        when(p.getId()).thenReturn(7L);
        when(p.getBrandId()).thenReturn(1L);
        when(p.getName()).thenReturn("상품");
        when(p.getPrice()).thenReturn(new BigDecimal("1000"));
        when(p.getStockQuantity()).thenReturn(2);
        when(productRepository.findNotDeleted(eq(ProductSortOrder.LATEST), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 20), 1L));
        BrandModel brand = mock(BrandModel.class);
        when(brand.getName()).thenReturn("브랜드");
        when(brandService.findByIdAndNotDeletedIn(anyCollection())).thenReturn(Map.of(1L, brand));
        when(likeService.getLikeCountByProductIdsFromStats(anyCollection())).thenReturn(Map.of(7L, 1L));

        RankingPage result = svc.loadPage(LocalDate.of(2026, 3, 26), 1, 20);

        assertThat(result.listSource()).isEqualTo(RankingListSource.FALLBACK_DB_LATEST);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).productId()).isEqualTo(7L);
        assertThat(result.rows().get(0).score()).isEqualTo(0.0d);
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Redis 장애 시 상품 상세 순위는 null 매핑 가능한 empty를 반환한다.")
    void findOneBasedDailyRank_whenRedisUnavailable_shouldReturnEmpty() {
        when(rankingReadRepository.findOneBasedReverseRank("ranking:all:20260326", "101"))
                .thenThrow(new RedisConnectionFailureException("redis down"));

        OptionalLong result = rankingQueryService.findOneBasedDailyRank(LocalDate.of(2026, 3, 26), 101L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("RedisSystemException on count 시에도 랭킹 목록은 degraded 빈 페이지를 반환한다 (fallback off).")
    void loadPage_whenRedisSystemExceptionOnCount_shouldReturnEmptyDegradedPage() {
        RankingQueryService svc = newService(false);
        when(rankingReadRepository.count("ranking:all:20260326"))
                .thenThrow(new RedisSystemException("sys", new RuntimeException("inner")));

        RankingPage result = svc.loadPage(LocalDate.of(2026, 3, 26), 1, 20);

        assertThat(result.totalElements()).isZero();
        assertThat(result.totalPages()).isZero();
        assertThat(result.rows()).isEmpty();
        assertThat(result.listSource()).isEqualTo(RankingListSource.DEGRADED_EMPTY);
    }

    @Test
    @DisplayName("findOneBasedDailyRank: RedisSystemException 시 empty 반환 (R1).")
    void findOneBasedDailyRank_whenRedisSystemException_shouldReturnEmpty() {
        when(rankingReadRepository.findOneBasedReverseRank("ranking:all:20260326", "101"))
                .thenThrow(new RedisSystemException("sys", new RuntimeException("inner")));

        OptionalLong result = rankingQueryService.findOneBasedDailyRank(LocalDate.of(2026, 3, 26), 101L);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("member가 비숫자면 해당 슬롯은 행으로 만들지 않는다 (E-PARSE / R4).")
    void loadPage_whenMemberNotNumeric_shouldSkipUnparseableSlots() {
        LocalDate date = LocalDate.of(2026, 3, 26);
        when(rankingReadRepository.count("ranking:all:20260326")).thenReturn(2L);
        when(rankingReadRepository.findReverseRangeWithScores("ranking:all:20260326", 0L, 1L))
                .thenReturn(List.of(
                        new RankingZsetEntry("not-a-number", 9.0d),
                        new RankingZsetEntry("202", 0.1d)
                ));

        ProductModel p202 = mock(ProductModel.class);
        when(p202.getBrandId()).thenReturn(1L);
        when(p202.getName()).thenReturn("B");
        when(p202.getPrice()).thenReturn(new BigDecimal("2000"));
        when(p202.getStockQuantity()).thenReturn(0);
        when(productRepository.findByIdInAndNotDeletedAsMap(anyCollection()))
                .thenReturn(Map.of(202L, p202));
        BrandModel brand = mock(BrandModel.class);
        when(brand.getName()).thenReturn("브랜드");
        when(brandService.findByIdAndNotDeletedIn(anyCollection())).thenReturn(Map.of(1L, brand));
        when(likeService.getLikeCountByProductIdsFromStats(anyCollection())).thenReturn(Map.of());

        RankingPage result = rankingQueryService.loadPage(date, 1, 10);

        assertThat(result.totalElements()).isEqualTo(2L);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.listSource()).isEqualTo(RankingListSource.REDIS_ZSET);
        assertThat(result.rows().get(0).rank()).isEqualTo(2);
        assertThat(result.rows().get(0).productId()).isEqualTo(202L);
    }

    @Test
    @DisplayName("상품은 있으나 브랜드가 조회되지 않으면 행을 생략한다 (E-ZSET-ORPHAN / R3).")
    void loadPage_whenBrandMissing_shouldSkipRow() {
        LocalDate date = LocalDate.of(2026, 3, 26);
        when(rankingReadRepository.count("ranking:all:20260326")).thenReturn(1L);
        when(rankingReadRepository.findReverseRangeWithScores("ranking:all:20260326", 0L, 0L))
                .thenReturn(List.of(new RankingZsetEntry("303", 1.0d)));

        ProductModel p303 = mock(ProductModel.class);
        when(p303.getBrandId()).thenReturn(99L);
        when(productRepository.findByIdInAndNotDeletedAsMap(anyCollection()))
                .thenReturn(Map.of(303L, p303));
        when(brandService.findByIdAndNotDeletedIn(anyCollection())).thenReturn(Map.of());

        RankingPage result = rankingQueryService.loadPage(date, 1, 10);

        assertThat(result.totalElements()).isEqualTo(1L);
        assertThat(result.rows()).isEmpty();
        assertThat(result.listSource()).isEqualTo(RankingListSource.REDIS_ZSET);
    }

    @Test
    @DisplayName("ZCARD는 orphan 포함이나 Hydration 가능 행만 내려 totalElements와 행 수가 어긋날 수 있다 (E-TOTAL-MISMATCH / R3).")
    void loadPage_whenOneOrphanInZset_shouldKeepTotalAndOmitRow() {
        LocalDate date = LocalDate.of(2026, 3, 26);
        when(rankingReadRepository.count("ranking:all:20260326")).thenReturn(2L);
        when(rankingReadRepository.findReverseRangeWithScores("ranking:all:20260326", 0L, 1L))
                .thenReturn(List.of(
                        new RankingZsetEntry("999999", 2.0d),
                        new RankingZsetEntry("404", 1.0d)
                ));

        ProductModel p404 = mock(ProductModel.class);
        when(p404.getBrandId()).thenReturn(1L);
        when(p404.getName()).thenReturn("존재");
        when(p404.getPrice()).thenReturn(new BigDecimal("4000"));
        when(p404.getStockQuantity()).thenReturn(0);
        when(productRepository.findByIdInAndNotDeletedAsMap(anyCollection()))
                .thenReturn(Map.of(404L, p404));
        BrandModel brand = mock(BrandModel.class);
        when(brand.getName()).thenReturn("브랜드");
        when(brandService.findByIdAndNotDeletedIn(anyCollection())).thenReturn(Map.of(1L, brand));
        when(likeService.getLikeCountByProductIdsFromStats(anyCollection())).thenReturn(Map.of());

        RankingPage result = rankingQueryService.loadPage(date, 1, 10);

        assertThat(result.totalElements()).isEqualTo(2L);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.listSource()).isEqualTo(RankingListSource.REDIS_ZSET);
        assertThat(result.rows().get(0).rank()).isEqualTo(2);
        assertThat(result.rows().get(0).productId()).isEqualTo(404L);
    }

    @Test
    @DisplayName("count 성공 후 ZREVRANGE 실패 시 fallback off면 DEGRADED_EMPTY (R2 대체).")
    void loadPage_whenZrevrangeFails_andFallbackOff_shouldReturnDegradedEmpty() {
        RankingQueryService svc = newService(false);
        LocalDate date = LocalDate.of(2026, 3, 26);
        when(rankingReadRepository.count("ranking:all:20260326")).thenReturn(1L);
        when(rankingReadRepository.findReverseRangeWithScores("ranking:all:20260326", 0L, 0L))
                .thenThrow(new RedisConnectionFailureException("after count"));

        RankingPage result = svc.loadPage(date, 1, 10);

        assertThat(result.rows()).isEmpty();
        assertThat(result.listSource()).isEqualTo(RankingListSource.DEGRADED_EMPTY);
    }

    @Test
    @DisplayName("count 성공 후 ZREVRANGE 실패 시 fallback on이면 DB 최신순으로 대체한다.")
    void loadPage_whenZrevrangeFails_andFallbackOn_shouldReturnLatestFromDb() {
        RankingQueryService svc = newService(true);
        LocalDate date = LocalDate.of(2026, 3, 26);
        when(rankingReadRepository.count("ranking:all:20260326")).thenReturn(1L);
        when(rankingReadRepository.findReverseRangeWithScores("ranking:all:20260326", 0L, 0L))
                .thenThrow(new RedisConnectionFailureException("after count"));

        ProductModel p = mock(ProductModel.class);
        when(p.getId()).thenReturn(55L);
        when(p.getBrandId()).thenReturn(1L);
        when(p.getName()).thenReturn("대체");
        when(p.getPrice()).thenReturn(BigDecimal.TEN);
        when(p.getStockQuantity()).thenReturn(1);
        when(productRepository.findNotDeleted(eq(ProductSortOrder.LATEST), eq(null), any()))
                .thenReturn(new PageImpl<>(List.of(p), PageRequest.of(0, 10), 3L));
        BrandModel brand = mock(BrandModel.class);
        when(brand.getName()).thenReturn("B");
        when(brandService.findByIdAndNotDeletedIn(anyCollection())).thenReturn(Map.of(1L, brand));
        when(likeService.getLikeCountByProductIdsFromStats(anyCollection())).thenReturn(Map.of());

        RankingPage result = svc.loadPage(date, 1, 10);

        assertThat(result.listSource()).isEqualTo(RankingListSource.FALLBACK_DB_LATEST);
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).productId()).isEqualTo(55L);
    }
}

package com.loopers.application.ranking;

import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DisplayName("RankingApp 통합 테스트 — ZSET + DB 상품 enrichment")
class RankingAppIntegrationTest {

    @Autowired
    private RankingApp rankingApp;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
    }

    @Test
    @DisplayName("ZSET Top-N → DB 상품 조회 → RankingInfo 풍부화 전체 흐름")
    void topNEnrichmentFlow() {
        // given: 상품 3개 저장
        ProductModel p1 = productRepository.save(ProductModel.create("P001", 1L, "상품 A", new BigDecimal("10000"), 10));
        ProductModel p2 = productRepository.save(ProductModel.create("P002", 1L, "상품 B", new BigDecimal("20000"), 20));
        ProductModel p3 = productRepository.save(ProductModel.create("P003", 1L, "상품 C", new BigDecimal("30000"), 30));

        // given: ZSET에 점수 시드 (p2 > p1 > p3)
        LocalDate date = LocalDate.of(2026, 4, 5);
        String key = RankingKeyGenerator.dailyKey(date);
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 100.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 200.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(p3.getId()), 50.0);

        // when
        RankingPageResult result = rankingApp.getTopN(date, 0, 10);

        // then: score 내림차순 + 상품 정보 풍부화
        assertThat(result.items()).hasSize(3);
        assertThat(result.items().get(0).rank()).isEqualTo(1L);
        assertThat(result.items().get(0).productDbId()).isEqualTo(p2.getId());
        assertThat(result.items().get(0).productName()).isEqualTo("상품 B");
        assertThat(result.items().get(0).score()).isEqualTo(200.0);
        assertThat(result.items().get(0).status()).isEqualTo(RankingInfo.STATUS_ACTIVE);

        assertThat(result.items().get(1).rank()).isEqualTo(2L);
        assertThat(result.items().get(1).productDbId()).isEqualTo(p1.getId());

        assertThat(result.items().get(2).rank()).isEqualTo(3L);
        assertThat(result.items().get(2).productDbId()).isEqualTo(p3.getId());

        assertThat(result.totalElements()).isEqualTo(3L);
    }

    @Test
    @DisplayName("삭제된 상품은 DISCONTINUED 상태로 랭킹에 포함된다")
    void deletedProductIsDiscontinued() {
        // given: 상품 2개 중 1개 soft delete
        ProductModel p1 = productRepository.save(ProductModel.create("P001", 1L, "정상 상품", new BigDecimal("1000"), 10));
        ProductModel p2 = productRepository.save(ProductModel.create("P002", 1L, "삭제된 상품", new BigDecimal("1000"), 10));
        p2.delete();
        productRepository.save(p2);

        LocalDate date = LocalDate.of(2026, 4, 5);
        String key = RankingKeyGenerator.dailyKey(date);
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 500.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 1000.0);

        // when
        RankingPageResult result = rankingApp.getTopN(date, 0, 10);

        // then: 삭제 상품도 포함, DISCONTINUED 상태
        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).status()).isEqualTo(RankingInfo.STATUS_DISCONTINUED);
        assertThat(result.items().get(1).status()).isEqualTo(RankingInfo.STATUS_ACTIVE);
        assertThat(result.items().get(1).productName()).isEqualTo("정상 상품");
    }

    @Test
    @DisplayName("빈 랭킹 조회 → empty items (200 OK 상당)")
    void emptyRankingReturnsEmptyItems() {
        LocalDate date = LocalDate.of(2026, 4, 5);

        RankingPageResult result = rankingApp.getTopN(date, 0, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.totalElements()).isZero();
    }

    @Test
    @DisplayName("getProductRanking — ZSET에 있는 상품은 rank + score 반환")
    void productRankingFound() {
        ProductModel p = productRepository.save(ProductModel.create("P001", 1L, "상품", new BigDecimal("1000"), 10));
        LocalDate date = LocalDate.now();
        String key = RankingKeyGenerator.dailyKey(date);
        redisTemplate.opsForZSet().add(key, String.valueOf(p.getId()), 77.7);

        assertThat(rankingApp.getProductRanking(p.getId(), date))
                .isPresent()
                .get()
                .satisfies(info -> {
                    assertThat(info.rank()).isEqualTo(1L);
                    assertThat(info.score()).isEqualTo(77.7);
                });
    }

    @Test
    @DisplayName("getProductRanking — ZSET에 없는 상품은 Optional.empty() (미참여)")
    void productRankingNotFound() {
        LocalDate date = LocalDate.now();
        assertThat(rankingApp.getProductRanking(999L, date)).isEmpty();
    }
}

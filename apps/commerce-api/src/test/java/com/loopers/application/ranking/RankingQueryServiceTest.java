package com.loopers.application.ranking;

import com.loopers.config.TestRedisConfiguration;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestRedisConfiguration.class)
@DisplayName("RankingQueryService 테스트")
class RankingQueryServiceTest {

    @Autowired
    private RankingQueryService rankingQueryService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Brand testBrand;
    private Product product1;
    private Product product2;
    private Product product3;

    @BeforeEach
    void setUp() {
        cleanUpRedisKeys();

        testBrand = brandRepository.save(Brand.create("Test Brand", "Test", null));
        product1 = productRepository.save(Product.create(testBrand.getId(), "Product 1", "Desc", new Money(10000), new Stock(100), null));
        product2 = productRepository.save(Product.create(testBrand.getId(), "Product 2", "Desc", new Money(20000), new Stock(100), null));
        product3 = productRepository.save(Product.create(testBrand.getId(), "Product 3", "Desc", new Money(30000), new Stock(100), null));
    }

    @AfterEach
    void tearDown() {
        cleanUpRedisKeys();
        databaseCleanUp.truncateAllTables();
    }

    private void cleanUpRedisKeys() {
        Set<String> keys = redisTemplate.keys("ranking:*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private String getTodayKey() {
        return "ranking:all:" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    @Nested
    @DisplayName("랭킹 목록 조회")
    class GetRankings {

        @Test
        @DisplayName("점수 내림차순으로 랭킹을 조회한다")
        void 점수_내림차순_조회() {
            // Arrange - product3 > product1 > product2 순서로 점수 설정
            String key = getTodayKey();
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 10.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product2.getId()), 5.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product3.getId()), 20.0);

            // Act
            List<RankingResult> rankings = rankingQueryService.getRankings(LocalDate.now(), 10, 0);

            // Assert
            assertThat(rankings).hasSize(3);
            assertThat(rankings.get(0).productId()).isEqualTo(product3.getId());
            assertThat(rankings.get(0).rank()).isEqualTo(1);
            assertThat(rankings.get(0).score()).isEqualTo(20.0);
            assertThat(rankings.get(1).productId()).isEqualTo(product1.getId());
            assertThat(rankings.get(1).rank()).isEqualTo(2);
            assertThat(rankings.get(2).productId()).isEqualTo(product2.getId());
            assertThat(rankings.get(2).rank()).isEqualTo(3);
        }

        @Test
        @DisplayName("페이징 처리된 랭킹을 조회한다")
        void 페이징_조회() {
            // Arrange
            String key = getTodayKey();
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 30.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product2.getId()), 20.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product3.getId()), 10.0);

            // Act - 2번째 페이지 (offset=1, size=1)
            List<RankingResult> rankings = rankingQueryService.getRankings(LocalDate.now(), 1, 1);

            // Assert
            assertThat(rankings).hasSize(1);
            assertThat(rankings.get(0).productId()).isEqualTo(product2.getId());
            assertThat(rankings.get(0).rank()).isEqualTo(2); // 전체에서 2위
        }

        @Test
        @DisplayName("상품 정보를 함께 조회한다")
        void 상품_정보_포함() {
            // Arrange
            String key = getTodayKey();
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 10.0);

            // Act
            List<RankingResult> rankings = rankingQueryService.getRankings(LocalDate.now(), 10, 0);

            // Assert
            assertThat(rankings).hasSize(1);
            assertThat(rankings.get(0).productName()).isEqualTo("Product 1");
            assertThat(rankings.get(0).productPrice()).isEqualTo(10000L);
        }

        @Test
        @DisplayName("랭킹 데이터가 없으면 빈 리스트를 반환한다")
        void 데이터_없으면_빈_리스트() {
            // Act
            List<RankingResult> rankings = rankingQueryService.getRankings(LocalDate.now(), 10, 0);

            // Assert
            assertThat(rankings).isEmpty();
        }

        @Test
        @DisplayName("삭제된 상품은 필터링한다")
        void 삭제된_상품_필터링() {
            // Arrange
            String key = getTodayKey();
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 10.0);
            redisTemplate.opsForZSet().add(key, "99999", 20.0); // 존재하지 않는 상품

            // Act
            List<RankingResult> rankings = rankingQueryService.getRankings(LocalDate.now(), 10, 0);

            // Assert - 존재하는 상품만 반환
            assertThat(rankings).hasSize(1);
            assertThat(rankings.get(0).productId()).isEqualTo(product1.getId());
        }
    }

    @Nested
    @DisplayName("랭킹 전체 개수 조회")
    class GetTotalCount {

        @Test
        @DisplayName("ZCARD로 전체 랭킹 개수를 조회한다")
        void 전체_개수_조회() {
            // Arrange
            String key = getTodayKey();
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 10.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product2.getId()), 20.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product3.getId()), 30.0);

            // Act
            long totalCount = rankingQueryService.getTotalCount(LocalDate.now());

            // Assert
            assertThat(totalCount).isEqualTo(3);
        }

        @Test
        @DisplayName("데이터가 없으면 0을 반환한다")
        void 데이터_없으면_0() {
            // Act
            long totalCount = rankingQueryService.getTotalCount(LocalDate.now());

            // Assert
            assertThat(totalCount).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("상품별 랭킹 정보 조회")
    class GetProductRank {

        @Test
        @DisplayName("상품의 현재 순위를 조회한다")
        void 상품_순위_조회() {
            // Arrange
            String key = getTodayKey();
            redisTemplate.opsForZSet().add(key, String.valueOf(product1.getId()), 30.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product2.getId()), 20.0);
            redisTemplate.opsForZSet().add(key, String.valueOf(product3.getId()), 10.0);

            // Act
            RankInfo rankInfo = rankingQueryService.getProductRank(product2.getId(), LocalDate.now());

            // Assert
            assertThat(rankInfo).isNotNull();
            assertThat(rankInfo.rank()).isEqualTo(2); // 1-based
            assertThat(rankInfo.score()).isEqualTo(20.0);
        }

        @Test
        @DisplayName("랭킹에 없는 상품은 null을 반환한다")
        void 랭킹_없는_상품_null() {
            // Act
            RankInfo rankInfo = rankingQueryService.getProductRank(product1.getId(), LocalDate.now());

            // Assert
            assertThat(rankInfo).isNull();
        }
    }
}

package com.loopers.application.ranking;

import com.loopers.config.redis.RankingKeys;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;



@SpringBootTest
class RankingFacadeIntegrationTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final String BRAND_NAME = "나이키";
    private static final int PRICE = 10000;
    private static final int STOCK = 10;

    @Autowired
    private RankingFacade rankingFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> redisTemplateMaster;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final int TEST_HOUR = 10;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisTemplateMaster.delete(RankingKeys.dailyKey(TODAY));
        redisTemplateMaster.delete(RankingKeys.hourlyKey(TODAY, TEST_HOUR));
        // Cache-Aside 캐시 키 전체 삭제 (loopers:ranking:* 패턴)
        redisTemplateMaster.execute((RedisCallback<Void>) connection -> {
            ScanOptions options = ScanOptions.scanOptions().match("loopers:ranking:*").count(100).build();
            try (var cursor = connection.scan(options)) {
                while (cursor.hasNext()) {
                    connection.del(cursor.next());
                }
            } catch (Exception ignored) {}
            return null;
        });
    }

    private String dailyKey() {
        return RankingKeys.dailyKey(TODAY);
    }

    @DisplayName("일간 랭킹 조회")
    @Nested
    class FindDailyRanking {

        @Test
        @DisplayName("Redis ZSET이 비어있으면 빈 목록과 totalElements=0을 반환한다")
        void returnsEmptyResult_whenZSetIsEmpty() {
            // act
            RankingResult result = rankingFacade.findDailyRanking(TODAY, 0, 20);

            // assert
            assertAll(
                    () -> assertThat(result.items()).isEmpty(),
                    () -> assertThat(result.totalElements()).isZero()
            );
        }

        @Test
        @DisplayName("score가 높은 상품이 rank 1위로 반환된다")
        void returnsHighestScoredProductAsRankOne() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand(BRAND_NAME));
            Product productA = productJpaRepository.save(new Product(brand.getId(), "에어맥스 A", new Money(PRICE), new Stock(STOCK)));
            Product productB = productJpaRepository.save(new Product(brand.getId(), "에어맥스 B", new Money(PRICE), new Stock(STOCK)));

            redisTemplateMaster.opsForZSet().add(dailyKey(), String.valueOf(productA.getId()), 100.0);
            redisTemplateMaster.opsForZSet().add(dailyKey(), String.valueOf(productB.getId()), 50.0);

            // act
            RankingResult result = rankingFacade.findDailyRanking(TODAY, 0, 20);

            // assert
            assertAll(
                    () -> assertThat(result.items()).hasSize(2),
                    () -> assertThat(result.items().get(0).rank()).isEqualTo(1),
                    () -> assertThat(result.items().get(0).productInfo().name()).isEqualTo("에어맥스 A"),
                    () -> assertThat(result.items().get(1).rank()).isEqualTo(2),
                    () -> assertThat(result.items().get(1).productInfo().name()).isEqualTo("에어맥스 B")
            );
        }

        @Test
        @DisplayName("ZSET에 있지만 DB에 없는 상품(삭제된 상품)은 결과에서 생략되고, rank 번호는 유지된다")
        void skipsDeletedProduct_andPreservesRankNumbers() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand(BRAND_NAME));
            Product productA = productJpaRepository.save(new Product(brand.getId(), "에어맥스 A", new Money(PRICE), new Stock(STOCK)));
            Product productC = productJpaRepository.save(new Product(brand.getId(), "에어맥스 C", new Money(PRICE), new Stock(STOCK)));

            Long deletedProductId = 99999L;  // DB에 존재하지 않는 productId

            // ZSET: productA(1위), deletedProduct(2위), productC(3위)
            redisTemplateMaster.opsForZSet().add(dailyKey(), String.valueOf(productA.getId()), 100.0);
            redisTemplateMaster.opsForZSet().add(dailyKey(), String.valueOf(deletedProductId), 75.0);
            redisTemplateMaster.opsForZSet().add(dailyKey(), String.valueOf(productC.getId()), 50.0);

            // act
            RankingResult result = rankingFacade.findDailyRanking(TODAY, 0, 20);

            // assert — 삭제 상품 스킵, productA=1위, productC=3위 (rank 번호 보존)
            assertAll(
                    () -> assertThat(result.items()).hasSize(2),
                    () -> assertThat(result.items().get(0).rank()).isEqualTo(1),
                    () -> assertThat(result.items().get(1).rank()).isEqualTo(3)
            );
        }

        @Test
        @DisplayName("page=1, size=2이면 3~4위 상품이 반환된다")
        void returnsPaginatedResults_forSecondPage() {
            // arrange — 5개 상품을 score 내림차순으로 ZSET에 추가
            Brand brand = brandJpaRepository.save(new Brand(BRAND_NAME));
            for (int i = 1; i <= 5; i++) {
                Product product = productJpaRepository.save(
                        new Product(brand.getId(), "상품 " + i, new Money(PRICE), new Stock(STOCK)));
                redisTemplateMaster.opsForZSet().add(dailyKey(), String.valueOf(product.getId()), 100.0 - i);
            }

            // act — page=1, size=2 → 오프셋 2부터 2개 = 3위, 4위
            RankingResult result = rankingFacade.findDailyRanking(TODAY, 1, 2);

            // assert
            assertAll(
                    () -> assertThat(result.items()).hasSize(2),
                    () -> assertThat(result.items().get(0).rank()).isEqualTo(3),
                    () -> assertThat(result.items().get(1).rank()).isEqualTo(4),
                    () -> assertThat(result.totalElements()).isEqualTo(5)
            );
        }

        @Test
        @DisplayName("브랜드명이 랭킹 아이템에 포함된다")
        void includesBrandName_inRankingItems() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand(BRAND_NAME));
            Product product = productJpaRepository.save(new Product(brand.getId(), "에어맥스", new Money(PRICE), new Stock(STOCK)));
            redisTemplateMaster.opsForZSet().add(dailyKey(), String.valueOf(product.getId()), 100.0);

            // act
            RankingResult result = rankingFacade.findDailyRanking(TODAY, 0, 20);

            // assert
            assertThat(result.items().get(0).productInfo().brandName()).isEqualTo(BRAND_NAME);
        }
    }

    @DisplayName("시간별 랭킹 조회")
    @Nested
    class FindHourlyRanking {

        @Test
        @DisplayName("Redis ZSET이 비어있으면 빈 목록과 totalElements=0을 반환한다")
        void returnsEmptyResult_whenZSetIsEmpty() {
            // act
            RankingResult result = rankingFacade.findHourlyRanking(TODAY, TEST_HOUR, 0, 20);

            // assert
            assertAll(
                    () -> assertThat(result.items()).isEmpty(),
                    () -> assertThat(result.totalElements()).isZero()
            );
        }

        @Test
        @DisplayName("score가 높은 상품이 rank 1위로 반환된다")
        void returnsHighestScoredProductAsRankOne() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand(BRAND_NAME));
            Product productA = productJpaRepository.save(new Product(brand.getId(), "에어맥스 A", new Money(PRICE), new Stock(STOCK)));
            Product productB = productJpaRepository.save(new Product(brand.getId(), "에어맥스 B", new Money(PRICE), new Stock(STOCK)));

            redisTemplateMaster.opsForZSet().add(RankingKeys.hourlyKey(TODAY, TEST_HOUR), String.valueOf(productA.getId()), 80.0);
            redisTemplateMaster.opsForZSet().add(RankingKeys.hourlyKey(TODAY, TEST_HOUR), String.valueOf(productB.getId()), 40.0);

            // act
            RankingResult result = rankingFacade.findHourlyRanking(TODAY, TEST_HOUR, 0, 20);

            // assert
            assertAll(
                    () -> assertThat(result.items()).hasSize(2),
                    () -> assertThat(result.items().get(0).rank()).isEqualTo(1),
                    () -> assertThat(result.items().get(0).productInfo().name()).isEqualTo("에어맥스 A"),
                    () -> assertThat(result.items().get(1).rank()).isEqualTo(2)
            );
        }
    }
}

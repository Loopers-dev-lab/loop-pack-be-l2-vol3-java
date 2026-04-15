package com.loopers.application.ranking;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest
@DisplayName("RankingFacade 통합 테스트")
class RankingFacadeTest {

    @Autowired
    private RankingFacade rankingFacade;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> masterRedisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private Brand savedBrand;
    private Product product1;
    private Product product2;
    private Product product3;
    private String todayKey;

    @BeforeEach
    void setUp() {
        savedBrand = brandRepository.save(new Brand("Apple", "애플", "https://example.com/apple.png"));
        product1 = productRepository.save(new Product("아이폰 15", savedBrand.getId(), 1L, 1500000L));
        product2 = productRepository.save(new Product("맥북 프로", savedBrand.getId(), 1L, 3000000L));
        product3 = productRepository.save(new Product("에어팟", savedBrand.getId(), 1L, 300000L));

        todayKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String redisKey = "ranking:all:" + todayKey;
        masterRedisTemplate.opsForZSet().add(redisKey, String.valueOf(product1.getId()), 30000.0);
        masterRedisTemplate.opsForZSet().add(redisKey, String.valueOf(product2.getId()), 500.0);
        masterRedisTemplate.opsForZSet().add(redisKey, String.valueOf(product3.getId()), 1.0);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("랭킹을 상품 정보와 함께 조회한다")
    void returnsRankingsWithProductInfo() {
        // Act
        Page<RankingInfo> result = rankingFacade.getRankings(todayKey, 0, 10);

        // Assert
        assertAll(
            () -> assertThat(result.getContent()).hasSize(3),
            () -> assertThat(result.getTotalElements()).isEqualTo(3),
            () -> assertThat(result.getContent().get(0).rank()).isEqualTo(1L),
            () -> assertThat(result.getContent().get(0).score()).isEqualTo(30000.0),
            () -> assertThat(result.getContent().get(0).productName()).isEqualTo("아이폰 15"),
            () -> assertThat(result.getContent().get(0).brandName()).isEqualTo("Apple"),
            () -> assertThat(result.getContent().get(1).rank()).isEqualTo(2L),
            () -> assertThat(result.getContent().get(2).rank()).isEqualTo(3L)
        );
    }

    @Test
    @DisplayName("페이징이 정상 동작한다")
    void returnsPaginatedRankings() {
        // Act
        Page<RankingInfo> result = rankingFacade.getRankings(todayKey, 0, 2);

        // Assert
        assertAll(
            () -> assertThat(result.getContent()).hasSize(2),
            () -> assertThat(result.getTotalElements()).isEqualTo(3),
            () -> assertThat(result.getTotalPages()).isEqualTo(2),
            () -> assertThat(result.getContent().get(0).rank()).isEqualTo(1L),
            () -> assertThat(result.getContent().get(1).rank()).isEqualTo(2L)
        );
    }

    @Test
    @DisplayName("date가 null이면 오늘 날짜로 조회한다")
    void usesTodayDateWhenNull() {
        // Act
        Page<RankingInfo> result = rankingFacade.getRankings(null, 0, 10);

        // Assert
        assertThat(result.getContent()).hasSize(3);
    }

    @Test
    @DisplayName("랭킹 데이터가 없으면 빈 결과를 반환한다")
    void returnsEmptyWhenNoRankings() {
        // Act
        Page<RankingInfo> result = rankingFacade.getRankings("99991231", 0, 10);

        // Assert
        assertAll(
            () -> assertThat(result.getContent()).isEmpty(),
            () -> assertThat(result.getTotalElements()).isEqualTo(0)
        );
    }

    @Test
    @DisplayName("특정 상품의 랭킹 정보를 조회한다")
    void returnsProductRanking() {
        // Act
        RankingDetailInfo result = rankingFacade.getProductRanking(product1.getId());

        // Assert
        assertAll(
            () -> assertThat(result).isNotNull(),
            () -> assertThat(result.rank()).isEqualTo(1L),
            () -> assertThat(result.score()).isEqualTo(30000.0),
            () -> assertThat(result.date()).isEqualTo(todayKey)
        );
    }

    @Test
    @DisplayName("랭킹에 없는 상품은 null을 반환한다")
    void returnsNullForUnrankedProduct() {
        // Act
        RankingDetailInfo result = rankingFacade.getProductRanking(999L);

        // Assert
        assertThat(result).isNull();
    }
}

package com.loopers.interfaces.api.ranking.v1;

import static com.loopers.interfaces.api.ranking.v1.RankingSteps.getRankings;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;

import com.loopers.interfaces.api.brand.v1.BrandDto;
import com.loopers.interfaces.api.brand.v1.BrandSteps;
import com.loopers.interfaces.api.product.v1.ProductDto;
import com.loopers.interfaces.api.product.v1.ProductSteps;
import com.loopers.support.BaseE2ETest;

@DisplayName("GET /api/v1/rankings")
class RankingV1ApiE2ETest extends BaseE2ETest {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private String today;
    private String rankingKey;

    @BeforeEach
    void setUp() {
        today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        rankingKey = "ranking:v1:all:" + today;
    }

    @DisplayName("랭킹 데이터가 있으면,")
    @Nested
    class WhenRankingDataExists {

        private Long productId1;
        private Long productId2;
        private Long productId3;

        @BeforeEach
        void setUp() {
            Long brandId = BrandSteps.createBrand(
                    testRestTemplate,
                    new BrandDto.CreateBrandRequest("테스트 브랜드", "https://example.com/logo.png", null)
            );
            productId1 = ProductSteps.createProduct(testRestTemplate,
                    new ProductDto.CreateProductRequest(brandId, "상품1", "https://example.com/1.png", 50000L, 100L, null));
            productId2 = ProductSteps.createProduct(testRestTemplate,
                    new ProductDto.CreateProductRequest(brandId, "상품2", "https://example.com/2.png", 30000L, 100L, null));
            productId3 = ProductSteps.createProduct(testRestTemplate,
                    new ProductDto.CreateProductRequest(brandId, "상품3", "https://example.com/3.png", 10000L, 100L, null));

            redisTemplate.opsForZSet().add(rankingKey, String.valueOf(productId1), 70.0);
            redisTemplate.opsForZSet().add(rankingKey, String.valueOf(productId2), 58.4);
            redisTemplate.opsForZSet().add(rankingKey, String.valueOf(productId3), 45.2);
        }

        @DisplayName("순위순으로 상품 정보를 반환한다.")
        @Test
        void returnsRankedProducts() {
            // act
            var response = getRankings(testRestTemplate, "");

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().rankings()).hasSize(3),
                    () -> assertThat(response.getBody().data().rankings().get(0).rank()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(productId1),
                    () -> assertThat(response.getBody().data().rankings().get(0).productName()).isEqualTo("상품1"),
                    () -> assertThat(response.getBody().data().totalCount()).isEqualTo(3)
            );
        }

        @DisplayName("페이지네이션이 적용된다.")
        @Test
        void supportsPagination() {
            // act
            var response = getRankings(testRestTemplate, "page=1&size=2");

            // assert
            List<RankingDto.RankedProductResponse> rankings = response.getBody().data().rankings();
            assertAll(
                    () -> assertThat(rankings).hasSize(1),
                    () -> assertThat(rankings.get(0).rank()).isEqualTo(3),
                    () -> assertThat(rankings.get(0).productId()).isEqualTo(productId3),
                    () -> assertThat(response.getBody().data().page()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().size()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().totalCount()).isEqualTo(3)
            );
        }
    }

    @DisplayName("랭킹 데이터가 없으면, 빈 배열을 반환한다.")
    @Test
    void returnsEmptyRankings_whenNoData() {
        // act
        var response = getRankings(testRestTemplate, "");

        // assert
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().rankings()).isEmpty(),
                () -> assertThat(response.getBody().data().totalCount()).isZero()
        );
    }

    @DisplayName("특정 날짜를 지정하면, 해당 날짜의 랭킹을 조회한다.")
    @Test
    void returnsRankingsForSpecificDate() {
        // arrange
        String specificDate = "20250101";
        String specificKey = "ranking:v1:all:" + specificDate;
        Long brandId = BrandSteps.createBrand(
                testRestTemplate,
                new BrandDto.CreateBrandRequest("브랜드", "https://example.com/logo.png", null)
        );
        Long productId = ProductSteps.createProduct(testRestTemplate,
                new ProductDto.CreateProductRequest(brandId, "상품", "https://example.com/1.png", 10000L, 100L, null));
        redisTemplate.opsForZSet().add(specificKey, String.valueOf(productId), 99.0);

        // act
        var response = getRankings(testRestTemplate, "date=" + specificDate);

        // assert
        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().rankings()).hasSize(1),
                () -> assertThat(response.getBody().data().rankings().get(0).productId()).isEqualTo(productId)
        );
    }
}

package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.product.ProductV1Dto;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

	private static final String KEY_PREFIX = "ranking:all:";
	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

	private final TestRestTemplate testRestTemplate;
	private final BrandService brandService;
	private final ProductService productService;
	private final DatabaseCleanUp databaseCleanUp;

	@Autowired
	@Qualifier("redisTemplateMaster")
	private RedisTemplate<String, String> masterRedisTemplate;

	@Autowired
	public RankingV1ApiE2ETest(
			TestRestTemplate testRestTemplate,
			BrandService brandService,
			ProductService productService,
			DatabaseCleanUp databaseCleanUp
	) {
		this.testRestTemplate = testRestTemplate;
		this.brandService = brandService;
		this.productService = productService;
		this.databaseCleanUp = databaseCleanUp;
	}

	@BeforeEach
	void setUp() {
		cleanRedis();
	}

	@AfterEach
	void tearDown() {
		databaseCleanUp.truncateAllTables();
		cleanRedis();
	}

	private void cleanRedis() {
		Set<String> rankingKeys = masterRedisTemplate.keys(KEY_PREFIX + "*");
		if (rankingKeys != null && !rankingKeys.isEmpty()) {
			masterRedisTemplate.delete(rankingKeys);
		}
		Set<String> cacheKeys = masterRedisTemplate.keys("product:*");
		if (cacheKeys != null && !cacheKeys.isEmpty()) {
			masterRedisTemplate.delete(cacheKeys);
		}
	}

	private String todayDate() {
		return LocalDate.now().format(DATE_FORMAT);
	}

	private String todayKey() {
		return KEY_PREFIX + todayDate();
	}

	@DisplayName("GET /api/v1/rankings — 랭킹 페이지 조회")
	@Nested
	class GetRankings {

		@DisplayName("ZSET에 점수가 누적된 상품을 조회하면 순위/점수/상품정보가 Aggregation되어 반환된다")
		@Test
		void rankingsWithProductAggregation() {
			// given
			BrandModel brand = brandService.register("나이키", "나이키 설명", "nike.png");
			ProductModel p1 = productService.register(brand.getId(), "에어맥스", "설명1", 10000, 100, "airmax.png");
			ProductModel p2 = productService.register(brand.getId(), "덩크로우", "설명2", 12000, 50, "dunk.png");
			ProductModel p3 = productService.register(brand.getId(), "조던1", "설명3", 15000, 30, "jordan.png");

			String key = todayKey();
			masterRedisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 3.2);
			masterRedisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 2.8);
			masterRedisTemplate.opsForZSet().add(key, String.valueOf(p3.getId()), 1.5);

			// when
			ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> type =
					new ParameterizedTypeReference<>() {};
			ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
					testRestTemplate.exchange(
							"/api/v1/rankings?size=20&page=1",
							HttpMethod.GET, null, type
					);

			// then
			assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

			RankingV1Dto.RankingPageResponse data = response.getBody().data();
			assertThat(data.rankings()).hasSize(3);

			RankingV1Dto.RankingItemResponse first = data.rankings().get(0);
			assertAll(
					() -> assertThat(first.rank()).isEqualTo(1),
					() -> assertThat(first.score()).isEqualTo(3.2),
					() -> assertThat(first.productId()).isEqualTo(p1.getId()),
					() -> assertThat(first.productName()).isEqualTo("에어맥스"),
					() -> assertThat(first.brandName()).isEqualTo("나이키"),
					() -> assertThat(first.price()).isEqualTo(10000)
			);

			RankingV1Dto.RankingItemResponse second = data.rankings().get(1);
			assertThat(second.rank()).isEqualTo(2);
			assertThat(second.productId()).isEqualTo(p2.getId());
		}

		@DisplayName("어제 날짜의 랭킹을 조회하면 어제 데이터가 정상 반환된다")
		@Test
		void rankingsForYesterdayDate() {
			// given
			BrandModel brand = brandService.register("아디다스", "설명", "adidas.png");
			ProductModel product = productService.register(brand.getId(), "울트라부스트", "설명", 18000, 20, "ultra.png");

			String yesterdayDate = LocalDate.now().minusDays(1).format(DATE_FORMAT);
			String yesterdayKey = KEY_PREFIX + yesterdayDate;
			masterRedisTemplate.opsForZSet().add(yesterdayKey, String.valueOf(product.getId()), 5.0);

			// when
			ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> type =
					new ParameterizedTypeReference<>() {};
			ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
					testRestTemplate.exchange(
							"/api/v1/rankings?date=" + yesterdayDate + "&size=20&page=1",
							HttpMethod.GET, null, type
					);

			// then
			assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
			assertThat(response.getBody().data().rankings()).hasSize(1);
			assertThat(response.getBody().data().rankings().get(0).productName())
					.isEqualTo("울트라부스트");
		}

		@DisplayName("데이터가 없는 날짜를 조회하면 빈 랭킹이 반환된다")
		@Test
		void emptyRankingsForNoDataDate() {
			// when
			ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> type =
					new ParameterizedTypeReference<>() {};
			ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
					testRestTemplate.exchange(
							"/api/v1/rankings?date=20200101&size=20&page=1",
							HttpMethod.GET, null, type
					);

			// then
			assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
			assertThat(response.getBody().data().rankings()).isEmpty();
		}

		@DisplayName("가중치 합산 점수 순서대로 랭킹이 정렬된다 (주문 1건 > 좋아요 3건)")
		@Test
		void rankingOrderedByWeightedScore() {
			// given: 상품A에 주문 1건 점수(0.7), 상품B에 좋아요 3건 점수(0.6)
			BrandModel brand = brandService.register("나이키", "설명", "nike.png");
			ProductModel productA = productService.register(brand.getId(), "주문상품", "설명", 10000, 100, "a.png");
			ProductModel productB = productService.register(brand.getId(), "좋아요상품", "설명", 10000, 100, "b.png");

			String key = todayKey();
			masterRedisTemplate.opsForZSet().add(key, String.valueOf(productA.getId()), 0.7);
			masterRedisTemplate.opsForZSet().add(key, String.valueOf(productB.getId()), 0.6);

			// when
			ParameterizedTypeReference<ApiResponse<RankingV1Dto.RankingPageResponse>> type =
					new ParameterizedTypeReference<>() {};
			ResponseEntity<ApiResponse<RankingV1Dto.RankingPageResponse>> response =
					testRestTemplate.exchange(
							"/api/v1/rankings?size=20&page=1",
							HttpMethod.GET, null, type
					);

			// then: 주문 상품(0.7)이 좋아요 상품(0.6)보다 높은 순위
			assertThat(response.getBody().data().rankings().get(0).productName())
					.isEqualTo("주문상품");
			assertThat(response.getBody().data().rankings().get(1).productName())
					.isEqualTo("좋아요상품");
		}
	}

	@DisplayName("GET /api/v1/products/{id} — 상품 상세 + 순위")
	@Nested
	class GetProductWithRank {

		@DisplayName("ZSET에 있는 상품의 상세를 조회하면 rank가 포함된다")
		@Test
		void productInZsetHasRank() {
			// given
			BrandModel brand = brandService.register("나이키", "설명", "nike.png");
			ProductModel product = productService.register(brand.getId(), "에어포스1", "설명", 11000, 80, "af1.png");

			masterRedisTemplate.opsForZSet().add(todayKey(), String.valueOf(product.getId()), 5.0);

			// when
			ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>> type =
					new ParameterizedTypeReference<>() {};
			ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response =
					testRestTemplate.exchange(
							"/api/v1/products/" + product.getId(),
							HttpMethod.GET, null, type
					);

			// then
			assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
			assertThat(response.getBody().data().rank()).isEqualTo(1L);
		}

		@DisplayName("ZSET에 없는 상품의 상세를 조회하면 rank가 null이다")
		@Test
		void productNotInZsetHasNullRank() {
			// given
			BrandModel brand = brandService.register("나이키", "설명", "nike.png");
			ProductModel product = productService.register(brand.getId(), "에어포스1", "설명", 11000, 80, "af1.png");

			// when
			ParameterizedTypeReference<ApiResponse<ProductV1Dto.ProductDetailResponse>> type =
					new ParameterizedTypeReference<>() {};
			ResponseEntity<ApiResponse<ProductV1Dto.ProductDetailResponse>> response =
					testRestTemplate.exchange(
							"/api/v1/products/" + product.getId(),
							HttpMethod.GET, null, type
					);

			// then
			assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
			assertThat(response.getBody().data().rank()).isNull();
		}
	}
}

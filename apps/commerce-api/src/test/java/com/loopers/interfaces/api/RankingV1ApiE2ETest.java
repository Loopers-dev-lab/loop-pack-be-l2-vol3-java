package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductStatus;
import com.loopers.domain.ranking.ProductRankMonthlyModel;
import com.loopers.domain.ranking.ProductRankWeeklyModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.ranking.MonthlyRankingJpaRepository;
import com.loopers.infrastructure.ranking.WeeklyRankingJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RankingV1ApiE2ETest {

    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter BASIC_HOUR = DateTimeFormatter.ofPattern("yyyyMMddHH");

    private final TestRestTemplate testRestTemplate;
    private final ProductJpaRepository productJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final WeeklyRankingJpaRepository weeklyRankingJpaRepository;
    private final MonthlyRankingJpaRepository monthlyRankingJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final RedisCleanUp redisCleanUp;
    private final RedisTemplate<String, String> redisTemplate;

    @Autowired
    RankingV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        ProductJpaRepository productJpaRepository,
        BrandJpaRepository brandJpaRepository,
        WeeklyRankingJpaRepository weeklyRankingJpaRepository,
        MonthlyRankingJpaRepository monthlyRankingJpaRepository,
        DatabaseCleanUp databaseCleanUp,
        RedisCleanUp redisCleanUp,
        @Qualifier("redisTemplateMaster") RedisTemplate<String, String> redisTemplate
    ) {
        this.testRestTemplate = testRestTemplate;
        this.productJpaRepository = productJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.weeklyRankingJpaRepository = weeklyRankingJpaRepository;
        this.monthlyRankingJpaRepository = monthlyRankingJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
        this.redisCleanUp = redisCleanUp;
        this.redisTemplate = redisTemplate;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("GET /api/v1/rankings - ZSET 점수 순으로 상품 정보를 함께 반환한다")
    @Test
    void returnsRankingPageWithProductInformation() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
        ProductModel p1 = productJpaRepository.save(new ProductModel(brand, "에어맥스", 150000L, "desc", 100, ProductStatus.ON_SALE));
        ProductModel p2 = productJpaRepository.save(new ProductModel(brand, "에어포스", 120000L, "desc", 100, ProductStatus.ON_SALE));
        ProductModel p3 = productJpaRepository.save(new ProductModel(brand, "리액트", 180000L, "desc", 100, ProductStatus.ON_SALE));

        String date = BASIC_DATE.format(LocalDate.now(ZoneId.of("Asia/Seoul")));
        String key = "ranking:all:" + date;
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 10.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 30.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(p3.getId()), 20.0);

        ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
            "/api/v1/rankings?date=" + date + "&size=2&page=1",
            HttpMethod.GET,
            null,
            responseType
        );

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().data().get("content");
        Map<String, Object> first = content.get(0);
        Map<String, Object> second = content.get(1);

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(content).hasSize(2),
            () -> assertThat(((Number) first.get("rank")).longValue()).isEqualTo(1L),
            () -> assertThat(((Map<String, Object>) first.get("product")).get("name")).isEqualTo("에어포스"),
            () -> assertThat(((Number) second.get("rank")).longValue()).isEqualTo(2L),
            () -> assertThat(((Map<String, Object>) second.get("product")).get("name")).isEqualTo("리액트")
        );
    }

    @DisplayName("GET /api/v1/rankings?date={어제} - 이전 날짜의 랭킹을 조회할 수 있다")
    @Test
    void returnsPreviousDateRanking() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("푸마", "스포츠 브랜드"));
        ProductModel p1 = productJpaRepository.save(new ProductModel(brand, "스웨이드", 90000L, "desc", 100, ProductStatus.ON_SALE));
        ProductModel p2 = productJpaRepository.save(new ProductModel(brand, "RS-X", 130000L, "desc", 100, ProductStatus.ON_SALE));

        LocalDate yesterday = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        String yesterdayStr = BASIC_DATE.format(yesterday);
        String key = "ranking:all:" + yesterdayStr;
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 50.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 80.0);

        ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
            "/api/v1/rankings?date=" + yesterdayStr + "&size=10&page=1",
            HttpMethod.GET,
            null,
            responseType
        );

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().data().get("content");

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().data().get("date")).isEqualTo(yesterdayStr),
            () -> assertThat(content).hasSize(2),
            () -> assertThat(((Map<String, Object>) content.get(0).get("product")).get("name")).isEqualTo("RS-X"),
            () -> assertThat(((Map<String, Object>) content.get(1).get("product")).get("name")).isEqualTo("스웨이드")
        );
    }

    @DisplayName("GET /api/v1/rankings - 가중치 기반으로 주문 > 조회 > 좋아요 순서로 정렬된다")
    @Test
    void returnsRankingOrderedByWeightBasedScore() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("뉴발란스", "스포츠 브랜드"));
        ProductModel pOrder = productJpaRepository.save(new ProductModel(brand, "992", 100000L, "desc", 100, ProductStatus.ON_SALE));
        ProductModel pLike = productJpaRepository.save(new ProductModel(brand, "574", 80000L, "desc", 100, ProductStatus.ON_SALE));
        ProductModel pView = productJpaRepository.save(new ProductModel(brand, "327", 70000L, "desc", 100, ProductStatus.ON_SALE));

        String date = BASIC_DATE.format(LocalDate.now(ZoneId.of("Asia/Seoul")));
        String key = "ranking:all:" + date;

        // 주문 1건 (price=10000, qty=1) → score = 10000 * 0.6 = 6000
        redisTemplate.opsForZSet().add(key, String.valueOf(pOrder.getId()), 6000.0);
        // 좋아요 3건 → score = 3 * 0.2 = 0.6
        redisTemplate.opsForZSet().add(key, String.valueOf(pLike.getId()), 0.6);
        // 조회 100건 → score = 100 * 0.1 = 10
        redisTemplate.opsForZSet().add(key, String.valueOf(pView.getId()), 10.0);

        ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
            "/api/v1/rankings?date=" + date + "&size=10&page=1",
            HttpMethod.GET,
            null,
            responseType
        );

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().data().get("content");

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(content).hasSize(3),
            () -> assertThat(((Map<String, Object>) content.get(0).get("product")).get("name")).isEqualTo("992"),
            () -> assertThat(((Number) content.get(0).get("score")).doubleValue()).isEqualTo(6000.0),
            () -> assertThat(((Map<String, Object>) content.get(1).get("product")).get("name")).isEqualTo("327"),
            () -> assertThat(((Number) content.get(1).get("score")).doubleValue()).isEqualTo(10.0),
            () -> assertThat(((Map<String, Object>) content.get(2).get("product")).get("name")).isEqualTo("574"),
            () -> assertThat(((Number) content.get(2).get("score")).doubleValue()).isEqualTo(0.6)
        );
    }

    @DisplayName("GET /api/v1/products/{id} - 당일 랭킹 정보가 함께 반환된다")
    @Test
    void returnsTodayRankOnProductDetail() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("아디다스", "스포츠 의류 및 신발 브랜드"));
        ProductModel p1 = productJpaRepository.save(new ProductModel(brand, "울트라부스트", 200000L, "desc", 100, ProductStatus.ON_SALE));
        ProductModel p2 = productJpaRepository.save(new ProductModel(brand, "삼바", 130000L, "desc", 100, ProductStatus.ON_SALE));

        String date = BASIC_DATE.format(LocalDate.now(ZoneId.of("Asia/Seoul")));
        String key = "ranking:all:" + date;
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 999.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 100.0);

        ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
            "/api/v1/products/" + p2.getId(),
            HttpMethod.GET,
            null,
            responseType
        );

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(((Number) response.getBody().data().get("ranking")).longValue()).isEqualTo(2L)
        );
    }

    @DisplayName("GET /api/v1/rankings/hourly - 시간별 랭킹을 조회할 수 있다")
    @Test
    void returnsHourlyRanking() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("리복", "스포츠 브랜드"));
        ProductModel p1 = productJpaRepository.save(new ProductModel(brand, "클래식", 70000L, "desc", 100, ProductStatus.ON_SALE));
        ProductModel p2 = productJpaRepository.save(new ProductModel(brand, "나노", 110000L, "desc", 100, ProductStatus.ON_SALE));

        LocalDateTime hour = LocalDateTime.now(ZoneId.of("Asia/Seoul")).withMinute(0).withSecond(0).withNano(0);
        String hourStr = BASIC_HOUR.format(hour);
        String key = "ranking:all:hour:" + hourStr;
        redisTemplate.opsForZSet().add(key, String.valueOf(p1.getId()), 3.0);
        redisTemplate.opsForZSet().add(key, String.valueOf(p2.getId()), 9.0);

        ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
            "/api/v1/rankings/hourly?hour=" + hourStr + "&size=10&page=1",
            HttpMethod.GET,
            null,
            responseType
        );

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().data().get("content");

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().data().get("hour")).isEqualTo(hourStr),
            () -> assertThat(content).hasSize(2),
            () -> assertThat(((Map<String, Object>) content.get(0).get("product")).get("name")).isEqualTo("나노"),
            () -> assertThat(((Map<String, Object>) content.get(1).get("product")).get("name")).isEqualTo("클래식")
        );
    }

    @DisplayName("GET /api/v1/rankings?period=WEEKLY - 주간 랭킹을 조회할 수 있다")
    @Test
    void returnsWeeklyRanking() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("컨버스", "스니커즈 브랜드"));
        ProductModel p1 = productJpaRepository.save(new ProductModel(brand, "척70", 90000L, "desc", 100, ProductStatus.ON_SALE));
        ProductModel p2 = productJpaRepository.save(new ProductModel(brand, "원스타", 110000L, "desc", 100, ProductStatus.ON_SALE));
        ProductModel p3 = productJpaRepository.save(new ProductModel(brand, "잭퍼셀", 80000L, "desc", 100, ProductStatus.ON_SALE));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String yearWeek = String.format("%d-W%02d",
            today.get(IsoFields.WEEK_BASED_YEAR),
            today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));

        weeklyRankingJpaRepository.save(new ProductRankWeeklyModel(p2.getId(), 1, 300.0, 1000, 50, 20, yearWeek));
        weeklyRankingJpaRepository.save(new ProductRankWeeklyModel(p1.getId(), 2, 200.0, 800, 30, 10, yearWeek));
        weeklyRankingJpaRepository.save(new ProductRankWeeklyModel(p3.getId(), 3, 100.0, 500, 20, 5, yearWeek));

        String date = BASIC_DATE.format(today);
        ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
            "/api/v1/rankings?period=WEEKLY&date=" + date + "&size=10&page=1",
            HttpMethod.GET,
            null,
            responseType
        );

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().data().get("content");

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(content).hasSize(3),
            () -> assertThat(((Map<String, Object>) content.get(0).get("product")).get("name")).isEqualTo("원스타"),
            () -> assertThat(((Number) content.get(0).get("rank")).longValue()).isEqualTo(1L),
            () -> assertThat(((Number) content.get(0).get("score")).doubleValue()).isEqualTo(300.0),
            () -> assertThat(((Map<String, Object>) content.get(1).get("product")).get("name")).isEqualTo("척70"),
            () -> assertThat(((Map<String, Object>) content.get(2).get("product")).get("name")).isEqualTo("잭퍼셀")
        );
    }

    @DisplayName("GET /api/v1/rankings?period=MONTHLY - 월간 랭킹을 조회할 수 있다")
    @Test
    void returnsMonthlyRanking() {
        BrandModel brand = brandJpaRepository.save(new BrandModel("반스", "스케이트 브랜드"));
        ProductModel p1 = productJpaRepository.save(new ProductModel(brand, "올드스쿨", 75000L, "desc", 100, ProductStatus.ON_SALE));
        ProductModel p2 = productJpaRepository.save(new ProductModel(brand, "어센틱", 65000L, "desc", 100, ProductStatus.ON_SALE));

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        String yearMonth = today.format(DateTimeFormatter.ofPattern("yyyy-MM"));

        monthlyRankingJpaRepository.save(new ProductRankMonthlyModel(p1.getId(), 1, 500.0, 2000, 100, 50, yearMonth));
        monthlyRankingJpaRepository.save(new ProductRankMonthlyModel(p2.getId(), 2, 300.0, 1000, 60, 30, yearMonth));

        String date = BASIC_DATE.format(today);
        ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
            "/api/v1/rankings?period=MONTHLY&date=" + date + "&size=10&page=1",
            HttpMethod.GET,
            null,
            responseType
        );

        List<Map<String, Object>> content = (List<Map<String, Object>>) response.getBody().data().get("content");

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(content).hasSize(2),
            () -> assertThat(((Map<String, Object>) content.get(0).get("product")).get("name")).isEqualTo("올드스쿨"),
            () -> assertThat(((Number) content.get(0).get("rank")).longValue()).isEqualTo(1L),
            () -> assertThat(((Number) content.get(0).get("score")).doubleValue()).isEqualTo(500.0),
            () -> assertThat(((Map<String, Object>) content.get(1).get("product")).get("name")).isEqualTo("어센틱")
        );
    }
}

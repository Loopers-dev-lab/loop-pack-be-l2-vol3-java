package com.loopers.interfaces.api.product;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.*;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.ranking.RankingV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class ProductV1ApiE2ETest {

    private static final String ENDPOINT_PRODUCTS = "/api/v1/products";
    private static final String LOGIN_ID = "productuser";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductService productService;
    @Autowired
    private LikeFacade likeFacade;
    @Autowired
    private UserService userService;
    @Autowired
    private RedisCleanUp redisCleanUp;
    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private Long productId;
    private Long brandId;
    private String brandName;

    @BeforeEach
    void setUp() {
        UserV1Dto.SignUpRequest signUp = new UserV1Dto.SignUpRequest(
            "productuser", "SecurePass1!", "product@example.com", "1990-01-15", "MALE");
        testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(signUp),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});

        BrandModel brand = brandService.registerBrand("E2E상품테스트브랜드");
        brandId = brand.getId();
        brandName = brand.getName();
        ProductModel product = productService.registerProduct(brandId, "E2E상품", new BigDecimal("15000"), 10);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("GET /api/v1/products/{productId} - 비로그인으로 조회 시 200 OK, brandName·likeCount 포함")
    void getProductDetail_withoutAuth_shouldReturn200WithBrandNameAndLikeCount() {
        ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> response = testRestTemplate.exchange(
            ENDPOINT_PRODUCTS + "/" + productId, HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {});

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody()).isNotNull(),
            () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
            () -> assertThat(response.getBody().data().id()).isEqualTo(productId),
            () -> assertThat(response.getBody().data().brandId()).isEqualTo(brandId),
            () -> assertThat(response.getBody().data().brandName()).isEqualTo(brandName),
            () -> assertThat(response.getBody().data().name()).isEqualTo("E2E상품"),
            () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(new BigDecimal("15000")),
            () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(10),
            () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0L),
            () -> assertThat(response.getBody().data().rankingRank()).isNull()
        );
    }

    @Test
    @DisplayName("GET /api/v1/products/{productId} - 좋아요가 있으면 likeCount가 반영된다")
    void getProductDetail_whenProductHasLikes_shouldReturnLikeCount() {
        UserModel user = userService.signUp(
            new UserId("likeuser"),
            new Email("like@test.com"),
            new BirthDate("1990-01-15"),
            Password.of("SecurePass1!", new BirthDate("1990-01-15")),
            Gender.MALE
        );
        likeFacade.addLike(user.getId(), productId);

        ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> response = testRestTemplate.exchange(
            ENDPOINT_PRODUCTS + "/" + productId, HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {});

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody()).isNotNull(),
            () -> assertThat(response.getBody().data().brandName()).isEqualTo(brandName),
            () -> assertThat(response.getBody().data().likeCount()).isEqualTo(1L)
        );
    }

    @Test
    @DisplayName("GET /api/v1/products/{productId} - ZSET에 있으면 rankingRank가 1-based로 반환된다")
    void getProductDetail_whenInRankingZset_shouldReturnRankingRank() {
        String dateStr = LocalDate.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.BASIC_ISO_DATE);
        String key = "ranking:all:" + dateStr;
        redisTemplate.opsForZSet().add(key, String.valueOf(productId), 1.0);

        ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> response = testRestTemplate.exchange(
            ENDPOINT_PRODUCTS + "/" + productId + "?date=" + dateStr, HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {});

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody()).isNotNull(),
            () -> assertThat(response.getBody().data().rankingRank()).isEqualTo(1L)
        );
    }

    @Test
    @DisplayName("GET /api/v1/products/{productId} - date 형식이 잘못되면 400")
    void getProductDetail_whenInvalidDate_shouldReturn400() {
        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
            ENDPOINT_PRODUCTS + "/" + productId + "?date=bad", HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /api/v1/products/{productId} - 존재하지 않는 상품 ID면 404 Not Found")
    void getProductDetail_whenNotFound_shouldReturn404() {
        long nonExistentId = 999_999L;

        ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> response = testRestTemplate.exchange(
            ENDPOINT_PRODUCTS + "/" + nonExistentId, HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("GET /api/v1/products - 목록 조회 시 200 OK, content·페이징 정보·brandName·likeCount 포함")
    void getProductList_shouldReturn200WithPagedContentAndBrandNameAndLikeCount() {
        ResponseEntity<ApiResponse<ProductV1Dto.ListResponse>> response = testRestTemplate.exchange(
            ENDPOINT_PRODUCTS + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {});

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody()).isNotNull(),
            () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
            () -> assertThat(response.getBody().data().content()).isNotEmpty(),
            () -> assertThat(response.getBody().data().content().get(0).brandName()).isEqualTo(brandName),
            () -> assertThat(response.getBody().data().content().get(0).likeCount()).isGreaterThanOrEqualTo(0),
            () -> assertThat(response.getBody().data().totalElements()).isGreaterThanOrEqualTo(1)
        );
    }

    @Test
    @DisplayName("GET /api/v1/products - sort=price_asc 시 가격 오름차순으로 반환된다")
    void getProductList_withPriceAsc_shouldReturnOrderedByPriceAsc() {
        ProductModel cheap = productService.registerProduct(brandId, "저가상품", new BigDecimal("5000"), 5);
        ProductModel expensive = productService.registerProduct(brandId, "고가상품", new BigDecimal("50000"), 5);

        ResponseEntity<ApiResponse<ProductV1Dto.ListResponse>> response = testRestTemplate.exchange(
            ENDPOINT_PRODUCTS + "?sort=price_asc&page=0&size=20", HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        var content = response.getBody().data().content();
        int cheapIdx = content.stream().map(ProductV1Dto.ListItemResponse::id).toList().indexOf(cheap.getId());
        int expensiveIdx = content.stream().map(ProductV1Dto.ListItemResponse::id).toList().indexOf(expensive.getId());
        assertThat(cheapIdx).isLessThan(expensiveIdx);
    }

    @Test
    @DisplayName("GET /api/v1/products - sort=likes_desc 시 좋아요 많은 순으로 반환된다")
    void getProductList_withLikesDesc_shouldReturnOrderedByLikesDesc() {
        Long productId2 = productService.registerProduct(brandId, "두번째상품", new BigDecimal("20000"), 5).getId();
        UserModel user = userService.signUp(
            new UserId("likeuser2"),
            new Email("like2@test.com"),
            new BirthDate("1990-01-15"),
            Password.of("SecurePass1!", new BirthDate("1990-01-15")),
            Gender.MALE
        );
        likeFacade.addLike(user.getId(), productId2);

        ResponseEntity<ApiResponse<ProductV1Dto.ListResponse>> response = testRestTemplate.exchange(
            ENDPOINT_PRODUCTS + "?sort=likes_desc&page=0&size=20", HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        var content = response.getBody().data().content();
        assertThat(content).isNotEmpty();
        assertThat(content.get(0).id()).isEqualTo(productId2);
        assertThat(content.get(0).likeCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("GET /api/v1/products - page가 음수면 400 Bad Request를 반환한다")
    void getProductList_withNegativePage_shouldReturn400() {
        ResponseEntity<ApiResponse<ProductV1Dto.ListResponse>> response = testRestTemplate.exchange(
            ENDPOINT_PRODUCTS + "?page=-1&size=20", HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /api/v1/products - size가 음수면 400 Bad Request를 반환한다")
    void getProductList_withNegativeSize_shouldReturn400() {
        ResponseEntity<ApiResponse<ProductV1Dto.ListResponse>> response = testRestTemplate.exchange(
            ENDPOINT_PRODUCTS + "?page=0&size=-1", HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("GET /api/v1/products/new-arrivals — 등록 최신순 신상 전용 목록")
    void getNewArrivals_shouldReturnLatestOrderedList() {
        ProductModel newer = productService.registerProduct(brandId, "더새상품", new BigDecimal("9000"), 2);

        ResponseEntity<ApiResponse<ProductV1Dto.ListResponse>> response = testRestTemplate.exchange(
            ENDPOINT_PRODUCTS + "/new-arrivals?page=0&size=20",
            HttpMethod.GET,
            new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                () -> assertThat(response.getBody().data().content().get(0).id()).isEqualTo(newer.getId())
        );
    }

    @Test
    @DisplayName("목록 조회 직후 ZSET 변경이 있으면 상세 rankingRank가 목록 rank와 다를 수 있다 (E-RANK-MISMATCH)")
    void getProductDetail_whenRankingChangesAfterList_shouldAllowRankMismatch() {
        BrandModel brand = brandService.registerBrand("순위불일치E2E");
        ProductModel first = productService.registerProduct(brand.getId(), "first", new BigDecimal("1000"), 5);
        ProductModel second = productService.registerProduct(brand.getId(), "second", new BigDecimal("2000"), 5);
        String date = "20260408";
        String key = "ranking:all:" + date;
        redisTemplate.opsForZSet().add(key, String.valueOf(first.getId()), 0.9d);
        redisTemplate.opsForZSet().add(key, String.valueOf(second.getId()), 0.8d);

        ResponseEntity<ApiResponse<RankingV1Dto.ListResponse>> listResponse = testRestTemplate.exchange(
                "/api/v1/rankings?date=" + date + "&page=1&size=10",
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});

        assertThat(listResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(listResponse.getBody()).isNotNull();
        assertThat(listResponse.getBody().data().content().get(0).productId()).isEqualTo(first.getId());
        assertThat(listResponse.getBody().data().content().get(0).rank()).isEqualTo(1);

        redisTemplate.opsForZSet().add(key, String.valueOf(second.getId()), 1.0d);

        ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> detailResponse = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS + "/" + first.getId() + "?date=" + date,
                HttpMethod.GET,
                new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(detailResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(detailResponse.getBody()).isNotNull(),
                () -> assertThat(detailResponse.getBody().data().rankingRank()).isEqualTo(2L)
        );
    }
}

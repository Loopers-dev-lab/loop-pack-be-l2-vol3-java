package com.loopers.interfaces.api.product;

import com.loopers.application.like.LikeFacade;
import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.user.*;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
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

    private Long productId;
    private Long brandId;
    private String brandName;

    @BeforeEach
    void setUp() {
        UserV1Dto.SignUpRequest signUp = new UserV1Dto.SignUpRequest(
            "productuser", "SecurePass1!", "product@example.com", "1990-01-15", "MALE");
        testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(signUp),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});

        BrandModel brand = brandService.register("E2E상품테스트브랜드");
        brandId = brand.getId();
        brandName = brand.getName();
        ProductModel product = productService.register(brandId, "E2E상품", new BigDecimal("15000"), 10);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
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
            () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0L)
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
    @DisplayName("GET /api/v1/products/{productId} - 존재하지 않는 상품 ID면 404 Not Found")
    void getProductDetail_whenNotFound_shouldReturn404() {
        long nonExistentId = 999_999L;

        ResponseEntity<ApiResponse<ProductV1Dto.DetailResponse>> response = testRestTemplate.exchange(
            ENDPOINT_PRODUCTS + "/" + nonExistentId, HttpMethod.GET, new HttpEntity<>(null),
            new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}

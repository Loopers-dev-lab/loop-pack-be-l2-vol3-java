package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class ProductApiE2ETest {

    private static final String ENDPOINT_PRODUCTS = "/api/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("상품 API")
    class ProductApi {

        @Test
        @DisplayName("상품 생성 후 상세 조회에 성공한다")
        void createAndGetDetail() {
            ProductDto.CreateProductRequest create = new ProductDto.CreateProductRequest(
                    "강아지 샴푸",
                    8900,
                    50,
                    "저자극",
                    1L,
                    10L
            );

            ResponseEntity<ApiResponse<ProductDto.ProductResponse>> created = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS,
                    HttpMethod.POST,
                    new HttpEntity<>(create),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            Long productId = created.getBody().data().id();

            ResponseEntity<ApiResponse<ProductDto.ProductResponse>> detail = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "/" + productId,
                    HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(detail.getBody().data().name()).isEqualTo("강아지 샴푸");
        }

        @Test
        @DisplayName("브랜드 필터로 목록 조회에 성공한다")
        void listWithBrandFilter() {
            create("상품A", 1000, 1L, 10L);
            create("상품B", 2000, 1L, 20L);

            ResponseEntity<ApiResponse<ProductDto.ProductListResponse>> list = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "?brandId=10&sort=latest&page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(list.getBody().data().totalElements()).isEqualTo(1);
            assertThat(list.getBody().data().items().get(0).brandId()).isEqualTo(10L);
        }
    }

    private void create(String name, int price, Long categoryId, Long brandId) {
        ProductDto.CreateProductRequest request = new ProductDto.CreateProductRequest(
                name,
                price,
                10,
                "desc",
                categoryId,
                brandId
        );

        testRestTemplate.exchange(
                ENDPOINT_PRODUCTS,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<ProductDto.ProductResponse>>() {
                }
        );
    }
}

package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.AdminBrandV1Dto;
import com.loopers.interfaces.api.product.AdminProductV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminProductV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/products";
    private static final String BRAND_ENDPOINT = "/api-admin/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public AdminProductV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    private Long brandId;

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    @BeforeEach
    void setUp() {
        AdminBrandV1Dto.CreateRequest brandRequest = new AdminBrandV1Dto.CreateRequest("나이키");
        ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> brandResponse = testRestTemplate.exchange(
            BRAND_ENDPOINT, HttpMethod.POST, new HttpEntity<>(brandRequest, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        brandId = brandResponse.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private AdminProductV1Dto.ProductResponse createProduct(String name, int price, int stock) {
        AdminProductV1Dto.CreateRequest request = new AdminProductV1Dto.CreateRequest(brandId, name, price, stock);
        ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
            ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data();
    }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class Create {

        @DisplayName("올바른 요청이면, 200 OK와 함께 상품 정보를 반환한다.")
        @Test
        void returnsProductInfo_whenValidRequest() {
            AdminProductV1Dto.CreateRequest request = new AdminProductV1Dto.CreateRequest(brandId, "에어맥스", 129000, 100);

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(129000),
                () -> assertThat(response.getBody().data().stock()).isEqualTo(100),
                () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0)
            );
        }

        @DisplayName("존재하지 않는 브랜드이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            AdminProductV1Dto.CreateRequest request = new AdminProductV1Dto.CreateRequest(999L, "에어맥스", 129000, 100);

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("이름이 비어있으면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            AdminProductV1Dto.CreateRequest request = new AdminProductV1Dto.CreateRequest(brandId, "", 129000, 100);

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class GetAll {

        @DisplayName("상품이 존재하면, 페이지 결과를 반환한다.")
        @Test
        void returnsPageResult_whenProductsExist() {
            createProduct("에어맥스", 129000, 100);
            createProduct("에어포스1", 109000, 200);
            createProduct("에어조던", 179000, 50);

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductPageResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=2", HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(3),
                () -> assertThat(response.getBody().data().totalPages()).isEqualTo(2)
            );
        }

        @DisplayName("브랜드별 필터링이 동작한다.")
        @Test
        void filtersByBrandId() {
            createProduct("에어맥스", 129000, 100);

            AdminBrandV1Dto.CreateRequest brand2Request = new AdminBrandV1Dto.CreateRequest("아디다스");
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> brand2Response = testRestTemplate.exchange(
                BRAND_ENDPOINT, HttpMethod.POST, new HttpEntity<>(brand2Request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            Long brand2Id = brand2Response.getBody().data().id();

            AdminProductV1Dto.CreateRequest product2 = new AdminProductV1Dto.CreateRequest(brand2Id, "울트라부스트", 159000, 50);
            testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(product2, adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ProductResponse>>() {}
            );

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductPageResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "?brandId=" + brandId, HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(response.getBody().data().content()).hasSize(1),
                () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("에어맥스")
            );
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    class GetById {

        @DisplayName("존재하는 상품이면, 상품 정보를 반환한다.")
        @Test
        void returnsProductInfo_whenProductExists() {
            AdminProductV1Dto.ProductResponse created = createProduct("에어맥스", 129000, 100);

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + created.id(), HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().stock()).isEqualTo(100),
                () -> assertThat(response.getBody().data().createdAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/999", HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    class Update {

        @DisplayName("올바른 요청이면, 수정된 상품 정보를 반환한다.")
        @Test
        void returnsUpdatedProduct_whenValidRequest() {
            AdminProductV1Dto.ProductResponse created = createProduct("에어맥스", 129000, 100);
            AdminProductV1Dto.UpdateRequest request = new AdminProductV1Dto.UpdateRequest("에어포스1", 109000, 200);

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + created.id(), HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어포스1"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(109000),
                () -> assertThat(response.getBody().data().stock()).isEqualTo(200),
                () -> assertThat(response.getBody().data().brandId()).isEqualTo(brandId)
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    class Delete {

        @DisplayName("존재하는 상품이면, 200 OK를 반환하고 조회되지 않는다.")
        @Test
        void deletesAndReturnsSuccess_whenProductExists() {
            AdminProductV1Dto.ProductResponse created = createProduct("에어맥스", 129000, 100);

            ResponseEntity<ApiResponse<Void>> deleteResponse = testRestTemplate.exchange(
                ENDPOINT + "/" + created.id(), HttpMethod.DELETE, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertTrue(deleteResponse.getStatusCode().is2xxSuccessful());

            ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> getResponse = testRestTemplate.exchange(
                ENDPOINT + "/" + created.id(), HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT + "/999", HttpMethod.DELETE, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}

package com.loopers.interfaces.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.product.dto.AdminProductV1Dto;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminProductV1ApiE2ETest {

    private static final String ENDPOINT_PRODUCTS = "/api-admin/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long brandId;

    @Autowired
    public AdminProductV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        BrandModel brand = brandJpaRepository.save(BrandModel.create("나이키"));
        brandId = brandJpaRepository.findByNameAndDeletedAtIsNull("나이키").get().getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private void registerProduct(String name, int price, int stock) {
        AdminProductV1Dto.RegisterRequest request = new AdminProductV1Dto.RegisterRequest(brandId, name, price, stock);
        ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
        testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType);
    }

    @DisplayName("LDAP 인증")
    @Nested
    class Authentication {

        @DisplayName("LDAP 헤더 없이 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoLdapHeader() {
            // arrange
            AdminProductV1Dto.RegisterRequest request = new AdminProductV1Dto.RegisterRequest(brandId, "에어맥스", 150000, 100);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(productJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("잘못된 LDAP 헤더 값으로 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenInvalidLdapHeader() {
            // arrange
            AdminProductV1Dto.RegisterRequest request = new AdminProductV1Dto.RegisterRequest(brandId, "에어맥스", 150000, 100);
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong.value");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(productJpaRepository.count()).isEqualTo(0)
            );
        }
    }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class Register {

        @DisplayName("유효한 상품 정보를 주면, 상품 등록에 성공한다.")
        @Test
        void returnsSuccess_whenValidProductInfoIsProvided() {
            // arrange
            AdminProductV1Dto.RegisterRequest request = new AdminProductV1Dto.RegisterRequest(brandId, "에어맥스", 150000, 100);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(productJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("상품명이 빈값이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenProductNameIsBlank() {
            // arrange
            AdminProductV1Dto.RegisterRequest request = new AdminProductV1Dto.RegisterRequest(brandId, "", 150000, 100);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(productJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("가격이 음수이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenPriceIsNegative() {
            // arrange
            AdminProductV1Dto.RegisterRequest request = new AdminProductV1Dto.RegisterRequest(brandId, "에어맥스", -1, 100);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(productJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("브랜드 ID가 null이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenBrandIdIsNull() {
            // arrange
            AdminProductV1Dto.RegisterRequest request = new AdminProductV1Dto.RegisterRequest(null, "에어맥스", 150000, 100);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(productJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // arrange
            AdminProductV1Dto.RegisterRequest request = new AdminProductV1Dto.RegisterRequest(999L, "에어맥스", 150000, 100);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                () -> assertThat(productJpaRepository.count()).isEqualTo(0)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class List {

        @DisplayName("상품 목록을 조회하면, 페이지네이션된 목록을 반환한다.")
        @Test
        void returnsPaginatedList_whenProductsExist() {
            // arrange
            registerProduct("에어맥스", 150000, 100);
            registerProduct("에어포스", 120000, 50);
            registerProduct("조던1", 200000, 30);

            // act
            ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ListResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminProductV1Dto.ListResponse>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS + "?page=0&size=2", HttpMethod.GET, new HttpEntity<>(null, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(3),
                () -> assertThat(response.getBody().data().totalPages()).isEqualTo(2),
                () -> assertThat(response.getBody().data().items()).hasSize(2)
            );
        }

        @DisplayName("브랜드 ID로 필터링하면, 해당 브랜드의 상품만 반환한다.")
        @Test
        void returnsFilteredList_whenBrandIdIsProvided() {
            // arrange
            BrandModel adidas = brandJpaRepository.save(BrandModel.create("아디다스"));
            Long adidasId = brandJpaRepository.findByNameAndDeletedAtIsNull("아디다스").get().getId();

            registerProduct("에어맥스", 150000, 100);

            AdminProductV1Dto.RegisterRequest adidasProduct = new AdminProductV1Dto.RegisterRequest(adidasId, "울트라부스트", 180000, 80);
            ParameterizedTypeReference<ApiResponse<Object>> registerType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_PRODUCTS, HttpMethod.POST, new HttpEntity<>(adidasProduct, adminHeaders()), registerType);

            // act
            ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ListResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminProductV1Dto.ListResponse>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS + "?brandId=" + brandId, HttpMethod.GET, new HttpEntity<>(null, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(1),
                () -> assertThat(response.getBody().data().items().get(0).name()).isEqualTo("에어맥스")
            );
        }

        @DisplayName("상품이 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoProductsExist() {
            // act
            ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.ListResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminProductV1Dto.ListResponse>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(null, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0),
                () -> assertThat(response.getBody().data().items()).isEmpty()
            );
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    class GetById {

        @DisplayName("존재하는 상품을 조회하면, 상세 정보를 반환한다.")
        @Test
        void returnsProductDetail_whenProductExists() {
            // arrange
            registerProduct("에어맥스", 150000, 100);
            Long productId = productJpaRepository.findAllByDeletedAtIsNull(org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0).getId();

            // act
            ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.DetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminProductV1Dto.DetailResponse>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS + "/" + productId, HttpMethod.GET, new HttpEntity<>(null, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(productId),
                () -> assertThat(response.getBody().data().brandId()).isEqualTo(brandId),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(150000),
                () -> assertThat(response.getBody().data().stock()).isEqualTo(100),
                () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                () -> assertThat(response.getBody().data().updatedAt()).isNotNull(),
                () -> assertThat(response.getBody().data().deletedAt()).isNull()
            );
        }

        @DisplayName("존재하지 않는 상품을 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS + "/999", HttpMethod.GET, new HttpEntity<>(null, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    class Update {

        @DisplayName("유효한 수정 요청이면, 상품이 수정된다.")
        @Test
        void returnsSuccess_whenValidUpdateRequest() {
            // arrange
            registerProduct("에어맥스", 150000, 100);
            Long productId = productJpaRepository.findAllByDeletedAtIsNull(org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0).getId();
            AdminProductV1Dto.UpdateRequest updateRequest = new AdminProductV1Dto.UpdateRequest("에어포스", 120000, 50);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS + "/" + productId, HttpMethod.PUT, new HttpEntity<>(updateRequest, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK)
            );

            // verify updated
            ParameterizedTypeReference<ApiResponse<AdminProductV1Dto.DetailResponse>> detailType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminProductV1Dto.DetailResponse>> detailResponse =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS + "/" + productId, HttpMethod.GET, new HttpEntity<>(null, adminHeaders()), detailType);

            assertAll(
                () -> assertThat(detailResponse.getBody().data().name()).isEqualTo("에어포스"),
                () -> assertThat(detailResponse.getBody().data().price()).isEqualTo(120000),
                () -> assertThat(detailResponse.getBody().data().stock()).isEqualTo(50)
            );
        }

        @DisplayName("존재하지 않는 상품을 수정하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // arrange
            AdminProductV1Dto.UpdateRequest updateRequest = new AdminProductV1Dto.UpdateRequest("에어포스", 120000, 50);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS + "/999", HttpMethod.PUT, new HttpEntity<>(updateRequest, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @DisplayName("상품명이 빈값이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenProductNameIsBlank() {
            // arrange
            registerProduct("에어맥스", 150000, 100);
            Long productId = productJpaRepository.findAllByDeletedAtIsNull(org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0).getId();
            AdminProductV1Dto.UpdateRequest updateRequest = new AdminProductV1Dto.UpdateRequest("", 120000, 50);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS + "/" + productId, HttpMethod.PUT, new HttpEntity<>(updateRequest, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    class Delete {

        @DisplayName("존재하는 상품을 삭제하면, 성공한다.")
        @Test
        void returnsSuccess_whenProductExists() {
            // arrange
            registerProduct("에어맥스", 150000, 100);
            Long productId = productJpaRepository.findAllByDeletedAtIsNull(org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent().get(0).getId();

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS + "/" + productId, HttpMethod.DELETE, new HttpEntity<>(null, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(productJpaRepository.findByIdAndDeletedAtIsNull(productId)).isEmpty()
            );
        }

        @DisplayName("존재하지 않는 상품을 삭제하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void throwsNotFound_whenProductDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_PRODUCTS + "/999", HttpMethod.DELETE, new HttpEntity<>(null, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }
    }
}

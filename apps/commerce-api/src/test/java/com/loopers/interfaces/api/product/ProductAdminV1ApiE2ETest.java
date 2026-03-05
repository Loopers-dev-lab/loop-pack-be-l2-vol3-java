package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.ResponseEntity;

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminV1ApiE2ETest {

    private static final String VALID_PRODUCT_NAME = "나이키 에어맥스";
    private static final String NEW_PRODUCT_NAME = "나이키 조던";
    private static final int VALID_PRICE = 10000;
    private static final int VALID_STOCK = 100;
    private static final Long NOT_EXISTED_BRAND_ID = 999L;
    private static final Long NOT_EXISTED_PRODUCT_ID = 999L;

    private static final String ENDPOINT_POST = "/api-admin/v1/products";
    private static final String ENDPOINT_GET_LIST = "/api-admin/v1/products";
    private static final Function<Long, String> ENDPOINT_PRODUCT = id -> "/api-admin/v1/products/" + id;

    private static final String HEADER_ADMIN_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;

    @Autowired
    public ProductAdminV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    HttpHeaders createAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_ADMIN_LDAP, ADMIN_LDAP_VALUE);
        return headers;
    }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class Register {

        @DisplayName("정상적인 상품 정보로 등록하면, 200 OK와 상품 정보를 반환한다.")
        @Test
        void returnsProductInfo_whenRegisterIsValid() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            HttpHeaders headers = createAdminHeaders();
            ProductAdminV1Dto.RegisterRequest request =
                    new ProductAdminV1Dto.RegisterRequest(brand.getId(), VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK);
            HttpEntity<ProductAdminV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request, headers);

            // act
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_POST, HttpMethod.POST, httpEntity, responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().id()).isPositive(),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(VALID_PRODUCT_NAME),
                    () -> assertThat(response.getBody().data().price()).isEqualTo(VALID_PRICE),
                    () -> assertThat(response.getBody().data().stock()).isEqualTo(VALID_STOCK)
            );
        }

        @DisplayName("관리자 인증 헤더가 누락되면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenHeaderIsMissing() {
            // arrange
            ProductAdminV1Dto.RegisterRequest request =
                    new ProductAdminV1Dto.RegisterRequest(1L, VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK);
            HttpEntity<ProductAdminV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request);

            // act
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_POST, HttpMethod.POST, httpEntity, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.UNAUTHORIZED.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("존재하지 않는 브랜드로 등록하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // arrange
            HttpHeaders headers = createAdminHeaders();
            ProductAdminV1Dto.RegisterRequest request =
                    new ProductAdminV1Dto.RegisterRequest(NOT_EXISTED_BRAND_ID, VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK);
            HttpEntity<ProductAdminV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request, headers);

            // act
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_POST, HttpMethod.POST, httpEntity, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("같은 브랜드에 중복된 상품명으로 등록하면, 409 CONFLICT 응답을 받는다.")
        @Test
        void returnsConflict_whenProductNameIsDuplicated() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            productJpaRepository.save(new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            HttpHeaders headers = createAdminHeaders();
            ProductAdminV1Dto.RegisterRequest request =
                    new ProductAdminV1Dto.RegisterRequest(brand.getId(), VALID_PRODUCT_NAME, VALID_PRICE, VALID_STOCK);
            HttpEntity<ProductAdminV1Dto.RegisterRequest> httpEntity = new HttpEntity<>(request, headers);

            // act
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_POST, HttpMethod.POST, httpEntity, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.CONFLICT.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/products/{id}")
    @Nested
    class GetProductDetails {

        @DisplayName("존재하는 productId로 요청하면, 200 OK와 상품 상세 정보를 반환한다.")
        @Test
        void returnsProductDetails_whenProductIdIsValid() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            HttpHeaders headers = createAdminHeaders();
            String requestUrl = ENDPOINT_PRODUCT.apply(product.getId());

            // act
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(product.getId()),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(VALID_PRODUCT_NAME),
                    () -> assertThat(response.getBody().data().stock()).isEqualTo(VALID_STOCK)
            );
        }

        @DisplayName("존재하지 않는 productId로 요청하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenProductIdDoesNotExist() {
            // arrange
            HttpHeaders headers = createAdminHeaders();
            String requestUrl = ENDPOINT_PRODUCT.apply(NOT_EXISTED_PRODUCT_ID);

            // act
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class GetProductList {

        @DisplayName("등록된 상품이 있을 때 목록을 조회하면, 200 OK와 상품 목록을 반환한다.")
        @Test
        void returnsProductList_whenProductsExist() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            HttpHeaders headers = createAdminHeaders();

            // act
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductListResponse>> response =
                    testRestTemplate.exchange(ENDPOINT_GET_LIST, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(1),
                    () -> assertThat(response.getBody().data().products().get(0).name()).isEqualTo(VALID_PRODUCT_NAME)
            );
        }

        @DisplayName("허용되지 않는 sort 값으로 요청하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenSortIsInvalid() {
            // arrange
            HttpHeaders headers = createAdminHeaders();
            String requestUrl = ENDPOINT_GET_LIST + "?sort=invalid";

            // act
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductListResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.GET, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.BAD_REQUEST.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{id}")
    @Nested
    class UpdateProduct {

        @DisplayName("중복되지 않는 상품명으로 수정하면, 200 OK와 수정된 상품 정보를 반환한다.")
        @Test
        void returnsProductInfo_whenUpdateIsValid() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            HttpHeaders headers = createAdminHeaders();
            ProductAdminV1Dto.UpdateRequest request = new ProductAdminV1Dto.UpdateRequest(NEW_PRODUCT_NAME, 20000, 50);
            HttpEntity<ProductAdminV1Dto.UpdateRequest> httpEntity = new HttpEntity<>(request, headers);
            String requestUrl = ENDPOINT_PRODUCT.apply(product.getId());

            // act
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.PUT, httpEntity, responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(NEW_PRODUCT_NAME),
                    () -> assertThat(response.getBody().data().price()).isEqualTo(20000)
            );
        }

        @DisplayName("같은 브랜드의 중복된 상품명으로 수정하면, 409 CONFLICT 응답을 받는다.")
        @Test
        void returnsConflict_whenProductNameIsDuplicated() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product1 = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            productJpaRepository.save(
                    new Product(brand.getId(), NEW_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            HttpHeaders headers = createAdminHeaders();
            ProductAdminV1Dto.UpdateRequest request = new ProductAdminV1Dto.UpdateRequest(NEW_PRODUCT_NAME, VALID_PRICE, VALID_STOCK);
            HttpEntity<ProductAdminV1Dto.UpdateRequest> httpEntity = new HttpEntity<>(request, headers);
            String requestUrl = ENDPOINT_PRODUCT.apply(product1.getId());

            // act
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.PUT, httpEntity, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.CONFLICT.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }

        @DisplayName("존재하지 않는 productId로 수정하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenProductIdDoesNotExist() {
            // arrange
            HttpHeaders headers = createAdminHeaders();
            ProductAdminV1Dto.UpdateRequest request = new ProductAdminV1Dto.UpdateRequest(NEW_PRODUCT_NAME, VALID_PRICE, VALID_STOCK);
            HttpEntity<ProductAdminV1Dto.UpdateRequest> httpEntity = new HttpEntity<>(request, headers);
            String requestUrl = ENDPOINT_PRODUCT.apply(NOT_EXISTED_PRODUCT_ID);

            // act
            ParameterizedTypeReference<ApiResponse<ProductAdminV1Dto.ProductResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.PUT, httpEntity, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{id}")
    @Nested
    class DeleteProduct {

        @DisplayName("존재하는 productId로 삭제하면, 200 OK를 반환한다.")
        @Test
        void returnsSuccess_whenDeleteIsValid() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), VALID_PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            HttpHeaders headers = createAdminHeaders();
            String requestUrl = ENDPOINT_PRODUCT.apply(product.getId());

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.DELETE, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS)
            );
        }

        @DisplayName("존재하지 않는 productId로 삭제하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenProductIdDoesNotExist() {
            // arrange
            HttpHeaders headers = createAdminHeaders();
            String requestUrl = ENDPOINT_PRODUCT.apply(NOT_EXISTED_PRODUCT_ID);

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(requestUrl, HttpMethod.DELETE, new HttpEntity<>(headers), responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }
}

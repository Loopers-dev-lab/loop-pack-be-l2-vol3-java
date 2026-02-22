package com.loopers.interfaces.api.product.v1;

import static com.loopers.interfaces.api.brand.v1.BrandSteps.createBrand;
import static com.loopers.interfaces.api.like.v1.LikeSteps.likeProduct;
import static com.loopers.interfaces.api.product.v1.ProductSteps.createProduct;
import static com.loopers.interfaces.api.product.v1.ProductSteps.deleteProduct;
import static com.loopers.interfaces.api.product.v1.ProductSteps.updateProduct;
import static com.loopers.interfaces.api.user.v1.UserSteps.signUp;
import static com.loopers.support.E2ETestHelper.adminAuthHeaders;
import static com.loopers.support.E2ETestHelper.assertErrorResponse;
import static com.loopers.support.E2ETestHelper.userAuthHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.brand.v1.BrandDto;
import com.loopers.interfaces.api.product.v1.ProductDto.ProductResponse;
import com.loopers.interfaces.api.user.v1.UserV1Dto;
import com.loopers.support.BaseE2ETest;
import com.loopers.support.error.ErrorType;

class ProductV1AdminApiE2ETest extends BaseE2ETest {

    private static final String PRODUCT_ADMIN_ENDPOINT = "/api-admin/v1/products";

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LikeRepository likeRepository;

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class CreateProduct {

        @DisplayName("유효한 정보를 입력하면, 상품 생성에 성공한다.")
        @Test
        void createsProduct_whenValidInputProvided() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var request = new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명");

            // act
            var response = createProduct(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().productId()).isNotNull()
            );
        }

        @DisplayName("상품 설명이 없으면, 상품 생성에 성공한다.")
        @Test
        void createsProduct_whenDescriptionIsNull() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var request = new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, null);

            // act
            var response = createProduct(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().productId()).isNotNull()
            );
        }

        @DisplayName("가격이 0이면, 상품 생성에 성공한다.")
        @Test
        void createsProduct_whenPriceIsZero() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var request = new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 0L, 100L, "상품 설명");

            // act
            var response = createProduct(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().productId()).isNotNull()
            );
        }

        @DisplayName("X-Loopers-Ldap 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoLdapHeader() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var request = new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명");

            // act
            var response = createProduct(testRestTemplate, request, new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("X-Loopers-Ldap 헤더 값이 잘못되면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenLdapHeaderValueIsWrong() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var request = new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명");
            var headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong.value");

            // act
            var response = createProduct(testRestTemplate, request, headers);

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("가격이 음수이면, INVALID_MONEY_AMOUNT 에러 응답을 받는다.")
        @Test
        void returnsInvalidMoneyAmount_whenPriceIsNegative() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var request = new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", -1L, 100L, "상품 설명");

            // act
            var response = createProduct(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_MONEY_AMOUNT);
        }

        @DisplayName("재고가 음수이면, INVALID_STOCK 에러 응답을 받는다.")
        @Test
        void returnsInvalidStock_whenStockIsNegative() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var request = new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, -1L, "상품 설명");

            // act
            var response = createProduct(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_STOCK);
        }

        @DisplayName("상품명이 빈 값이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var request = new ProductDto.CreateProductRequest(brandId, "", "https://example.com/thumb.png", 10000L, 100L, "상품 설명");

            // act
            var response = createProduct(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST);
        }

        @DisplayName("썸네일 URL이 빈 값이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenThumbnailUrlIsBlank() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var request = new ProductDto.CreateProductRequest(brandId, "상품명", "", 10000L, 100L, "상품 설명");

            // act
            var response = createProduct(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.BAD_REQUEST);
        }

        @DisplayName("상품명 길이가 유효하지 않으면, INVALID_PRODUCT_NAME 에러 응답을 받는다.")
        @ParameterizedTest(name = "길이가 {0}인 상품명")
        @ValueSource(ints = {1, 101})
        void returnsInvalidProductName_whenNameLengthIsInvalid(int length) {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var name = "a".repeat(length);
            var request = new ProductDto.CreateProductRequest(brandId, name, "https://example.com/thumb.png", 10000L, 100L, "상품 설명");

            // act
            var response = createProduct(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_PRODUCT_NAME);
        }

        @DisplayName("재고가 0이면, INVALID_STOCK 에러 응답을 받는다.")
        @Test
        void returnsInvalidStock_whenStockIsZero() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var request = new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 0L, "상품 설명");

            // act
            var response = createProduct(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_STOCK);
        }

        @DisplayName("존재하지 않는 브랜드 ID를 입력하면, BRAND_NOT_FOUND 에러 응답을 받는다.")
        @Test
        void returnsBrandNotFound_whenBrandDoesNotExist() {
            // arrange
            var request = new ProductDto.CreateProductRequest(999L, "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명");

            // act
            var response = createProduct(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.BRAND_NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드 ID를 입력하면, BRAND_NOT_FOUND 에러 응답을 받는다.")
        @Test
        void returnsBrandNotFound_whenBrandIsDeleted() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            Brand brand = brandRepository.findById(brandId).orElseThrow();
            brand.delete();
            brandRepository.save(brand);

            var request = new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명");

            // act
            var response = createProduct(testRestTemplate, request, adminAuthHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.BRAND_NOT_FOUND);
        }
    }

    @DisplayName("GET /api-admin/v1/products")
    @Nested
    class GetProducts {

        @DisplayName("brandId 없이 전체 상품을 조회할 수 있다.")
        @Test
        void returnsAllProducts_whenNoBrandIdFilter() {
            // arrange
            var brandId1 = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드1", "https://example.com/logo1.png", "설명"));
            var brandId2 = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드2", "https://example.com/logo2.png", "설명"));
            createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId1, "상품1", "https://example.com/thumb1.png", 10000L, 100L, "설명"));
            createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId2, "상품2", "https://example.com/thumb2.png", 20000L, 200L, "설명"));

            var url = UriComponentsBuilder.fromPath(PRODUCT_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 10)
                    .toUriString();

            // act
            var response = ProductSteps.getProducts(testRestTemplate, url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).hasSize(2)
            );
        }

        @DisplayName("brandId가 전달되면, 해당 브랜드의 상품만 조회된다.")
        @Test
        void returnsProductsByBrandId_whenBrandIdProvided() {
            // arrange
            var brandId1 = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드1", "https://example.com/logo1.png", "설명"));
            var brandId2 = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드2", "https://example.com/logo2.png", "설명"));
            createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId1, "상품1", "https://example.com/thumb1.png", 10000L, 100L, "설명"));
            createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId1, "상품2", "https://example.com/thumb2.png", 20000L, 200L, "설명"));
            createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId2, "상품3", "https://example.com/thumb3.png", 30000L, 300L, "설명"));

            var url = UriComponentsBuilder.fromPath(PRODUCT_ADMIN_ENDPOINT)
                    .queryParam("brandId", brandId1)
                    .queryParam("page", 0)
                    .queryParam("size", 10)
                    .toUriString();

            // act
            var response = ProductSteps.getProducts(testRestTemplate, url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content()).extracting(ProductResponse::brandId)
                            .containsOnly(brandId1)
            );
        }

        @DisplayName("페이지 크기보다 상품이 많으면, hasNext가 true이다.")
        @Test
        void returnsHasNextTrue_whenMoreProductsExist() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품1", "https://example.com/thumb1.png", 10000L, 100L, "설명"));
            createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품2", "https://example.com/thumb2.png", 20000L, 200L, "설명"));
            createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품3", "https://example.com/thumb3.png", 30000L, 300L, "설명"));

            var url = UriComponentsBuilder.fromPath(PRODUCT_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 2)
                    .toUriString();

            // act
            var response = ProductSteps.getProducts(testRestTemplate, url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().hasNext()).isTrue()
            );
        }

        @DisplayName("생성일 내림차순으로 정렬된다.")
        @Test
        void returnsProductsSortedByCreatedAtDesc() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "첫번째", "https://example.com/thumb1.png", 10000L, 100L, "설명"));
            createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "두번째", "https://example.com/thumb2.png", 20000L, 200L, "설명"));
            createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "세번째", "https://example.com/thumb3.png", 30000L, 300L, "설명"));

            var url = UriComponentsBuilder.fromPath(PRODUCT_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 10)
                    .toUriString();

            // act
            var response = ProductSteps.getProducts(testRestTemplate, url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).extracting(ProductResponse::name)
                            .containsExactly("세번째", "두번째", "첫번째")
            );
        }

        @DisplayName("상품이 없으면, 빈 목록이 반환된다.")
        @Test
        void returnsEmptyList_whenNoProductsExist() {
            // arrange
            var url = UriComponentsBuilder.fromPath(PRODUCT_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 10)
                    .toUriString();

            // act
            var response = ProductSteps.getProducts(testRestTemplate, url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().hasNext()).isFalse()
            );
        }

        @DisplayName("page/size 파라미터 없이 호출하면, 기본값으로 조회된다.")
        @Test
        void returnsDefaultPage_whenNoPageParams() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품1", "https://example.com/thumb1.png", 10000L, 100L, "설명"));

            // act
            var response = ProductSteps.getProducts(testRestTemplate, PRODUCT_ADMIN_ENDPOINT);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID로 필터링하면, BRAND_NOT_FOUND 에러 응답을 받는다.")
        @Test
        void returnsBrandNotFound_whenBrandDoesNotExist() {
            // arrange
            var url = UriComponentsBuilder.fromPath(PRODUCT_ADMIN_ENDPOINT)
                    .queryParam("brandId", 999)
                    .queryParam("page", 0)
                    .queryParam("size", 10)
                    .toUriString();

            // act
            var response = ProductSteps.getProducts(testRestTemplate, url);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.BRAND_NOT_FOUND);
        }
    }

    @DisplayName("GET /api-admin/v1/products/{productId}")
    @Nested
    class GetProduct {

        @DisplayName("존재하는 상품이면, 상품 정보를 조회할 수 있다.")
        @Test
        void returnsProductInfo_whenProductExists() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var productId = createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명"))
                    .getBody().data().productId();

            // act
            var response = ProductSteps.getProduct(testRestTemplate, productId);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(productId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("상품명"),
                    () -> assertThat(response.getBody().data().brandId()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().price()).isEqualTo(10000L),
                    () -> assertThat(response.getBody().data().stock()).isEqualTo(100L)
            );
        }

        @DisplayName("삭제된 상품이면, 삭제된 상품 정보를 조회할 수 있다.")
        @Test
        void returnsProductInfo_whenProductIsDeleted() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var productId = createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명"))
                    .getBody().data().productId();
            deleteProduct(testRestTemplate, productId);

            // act
            var response = ProductSteps.getProduct(testRestTemplate, productId);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(productId)
            );
        }

        @DisplayName("존재하지 않는 상품이면, PRODUCT_NOT_FOUND 에러 응답을 받는다.")
        @Test
        void returnsProductNotFound_whenProductDoesNotExist() {
            // act
            var response = ProductSteps.getProduct(testRestTemplate, 999L);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.PRODUCT_NOT_FOUND);
        }
    }

    @DisplayName("PUT /api-admin/v1/products/{productId}")
    @Nested
    class UpdateProduct {

        @DisplayName("유효한 정보를 입력하면, 상품 수정에 성공한다.")
        @Test
        void updatesProduct_whenValidInputProvided() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var productId = createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "설명"))
                    .getBody().data().productId();
            var request = new ProductDto.UpdateProductRequest("수정된 상품명", "https://example.com/new-thumb.png", 20000L, 200L, "수정된 설명");

            // act
            var response = updateProduct(testRestTemplate, productId, request);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().errorCode()).isNull()
            );

            Product updatedProduct = productRepository.findById(productId).orElseThrow();
            assertAll(
                    () -> assertThat(updatedProduct.getName().getValue()).isEqualTo("수정된 상품명"),
                    () -> assertThat(updatedProduct.getThumbnailUrl().getValue()).isEqualTo("https://example.com/new-thumb.png"),
                    () -> assertThat(updatedProduct.getPrice().getAmount()).isEqualTo(20000L),
                    () -> assertThat(updatedProduct.getStock().getValue()).isEqualTo(200L),
                    () -> assertThat(updatedProduct.getDescription()).isEqualTo("수정된 설명"),
                    () -> assertThat(updatedProduct.getBrandId()).isEqualTo(brandId)
            );
        }

        @DisplayName("X-Loopers-Ldap 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoLdapHeader() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var productId = createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "설명"))
                    .getBody().data().productId();
            var request = new ProductDto.UpdateProductRequest("수정된 상품명", "https://example.com/new-thumb.png", 20000L, 200L, "수정된 설명");

            // act
            var response = updateProduct(testRestTemplate, productId, request, new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("X-Loopers-Ldap 헤더 값이 잘못되면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenLdapHeaderValueIsWrong() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var productId = createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "설명"))
                    .getBody().data().productId();
            var request = new ProductDto.UpdateProductRequest("수정된 상품명", "https://example.com/new-thumb.png", 20000L, 200L, "수정된 설명");
            var headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong.value");

            // act
            var response = updateProduct(testRestTemplate, productId, request, headers);

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 상품이면, PRODUCT_NOT_FOUND 에러 응답을 받는다.")
        @Test
        void returnsProductNotFound_whenProductDoesNotExist() {
            // arrange
            var request = new ProductDto.UpdateProductRequest("수정된 상품명", "https://example.com/new-thumb.png", 20000L, 200L, "수정된 설명");

            // act
            var response = updateProduct(testRestTemplate, 999L, request);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.PRODUCT_NOT_FOUND);
        }

        @DisplayName("이미 삭제된 상품을 수정하면, ALREADY_DELETED_PRODUCT 에러 응답을 받는다.")
        @Test
        void returnsAlreadyDeleted_whenProductIsAlreadyDeleted() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var productId = createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "설명"))
                    .getBody().data().productId();
            deleteProduct(testRestTemplate, productId);
            var request = new ProductDto.UpdateProductRequest("수정된 상품명", "https://example.com/new-thumb.png", 20000L, 200L, "수정된 설명");

            // act
            var response = updateProduct(testRestTemplate, productId, request);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.ALREADY_DELETED_PRODUCT);
        }

        @DisplayName("상품명 길이가 유효하지 않으면, INVALID_PRODUCT_NAME 에러 응답을 받는다.")
        @ParameterizedTest(name = "길이가 {0}인 상품명")
        @ValueSource(ints = {1, 101})
        void returnsInvalidProductName_whenNameLengthIsInvalid(int length) {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var productId = createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "설명"))
                    .getBody().data().productId();
            var name = "a".repeat(length);
            var request = new ProductDto.UpdateProductRequest(name, "https://example.com/new-thumb.png", 20000L, 200L, "수정된 설명");

            // act
            var response = updateProduct(testRestTemplate, productId, request);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_PRODUCT_NAME);
        }

        @DisplayName("가격이 음수이면, INVALID_MONEY_AMOUNT 에러 응답을 받는다.")
        @Test
        void returnsInvalidMoneyAmount_whenPriceIsNegative() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var productId = createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "설명"))
                    .getBody().data().productId();
            var request = new ProductDto.UpdateProductRequest("수정된 상품명", "https://example.com/new-thumb.png", -1L, 200L, "수정된 설명");

            // act
            var response = updateProduct(testRestTemplate, productId, request);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_MONEY_AMOUNT);
        }

        @DisplayName("재고가 0이면, INVALID_STOCK 에러 응답을 받는다.")
        @Test
        void returnsInvalidStock_whenStockIsZero() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var productId = createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "설명"))
                    .getBody().data().productId();
            var request = new ProductDto.UpdateProductRequest("수정된 상품명", "https://example.com/new-thumb.png", 20000L, 0L, "수정된 설명");

            // act
            var response = updateProduct(testRestTemplate, productId, request);

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_STOCK);
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{productId}")
    @Nested
    class DeleteProduct {

        @DisplayName("유효한 상품을 삭제하면, 200 성공 응답을 받는다.")
        @Test
        void returnsSuccess_whenProductExists() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var productId = createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "설명"))
                    .getBody().data().productId();

            // act
            var response = deleteProduct(testRestTemplate, productId);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().errorCode()).isNull()
            );

            Product deletedProduct = productRepository.findById(productId).orElseThrow();
            assertThat(deletedProduct.getDeletedAt()).isNotNull();
        }

        @DisplayName("좋아요가 있는 상품을 삭제하면, 좋아요도 함께 삭제된다.")
        @Test
        void deletesLikesWithProduct_whenProductHasLikes() {
            // arrange
            var signUpRequest = new UserV1Dto.SignUpRequest("testuser1", "Password1!", "홍길동", "1990-01-15", "test@example.com");
            signUp(testRestTemplate, signUpRequest);
            var userHeaders = userAuthHeaders(signUpRequest.loginId(), signUpRequest.password());

            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var productId = createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "설명"))
                    .getBody().data().productId();

            likeProduct(testRestTemplate, productId, userHeaders);

            // act
            var response = deleteProduct(testRestTemplate, productId);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().errorCode()).isNull()
            );

            assertThat(likeRepository.existsByUserIdAndProductId(1L, productId)).isFalse();
        }

        @DisplayName("존재하지 않는 상품을 삭제하면, PRODUCT_NOT_FOUND 에러 응답을 받는다.")
        @Test
        void returnsProductNotFound_whenProductDoesNotExist() {
            // act
            var response = deleteProduct(testRestTemplate, 999L);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.PRODUCT_NOT_FOUND);
        }

        @DisplayName("이미 삭제된 상품을 삭제하면, 200 응답을 받는다.")
        @Test
        void returnsOk_whenProductIsAlreadyDeleted() {
            // arrange
            var brandId = createBrand(testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "설명"));
            var productId = createProduct(testRestTemplate, new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "설명"))
                    .getBody().data().productId();
            deleteProduct(testRestTemplate, productId);

            // act
            var response = deleteProduct(testRestTemplate, productId);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }
}

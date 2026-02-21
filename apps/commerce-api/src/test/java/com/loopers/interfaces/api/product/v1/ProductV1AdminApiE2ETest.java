package com.loopers.interfaces.api.product.v1;

import static com.loopers.interfaces.api.brand.v1.BrandSteps.createBrand;
import static com.loopers.interfaces.api.product.v1.ProductSteps.createProduct;
import static com.loopers.support.E2ETestHelper.adminAuthHeaders;
import static com.loopers.support.E2ETestHelper.assertErrorResponse;
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

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.interfaces.api.brand.v1.BrandDto;
import com.loopers.support.BaseE2ETest;
import com.loopers.support.error.ErrorType;

class ProductV1AdminApiE2ETest extends BaseE2ETest {

    @Autowired
    private BrandRepository brandRepository;

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
}

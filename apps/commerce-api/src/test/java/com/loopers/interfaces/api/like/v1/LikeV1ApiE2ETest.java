package com.loopers.interfaces.api.like.v1;

import static com.loopers.interfaces.api.like.v1.LikeSteps.getLikedProducts;
import static com.loopers.interfaces.api.like.v1.LikeSteps.likeProduct;
import static com.loopers.interfaces.api.like.v1.LikeSteps.unlikeProduct;
import static com.loopers.interfaces.api.product.v1.ProductSteps.getActiveProduct;
import static com.loopers.interfaces.api.user.v1.UserSteps.signUp;
import static com.loopers.support.E2ETestHelper.assertErrorResponse;
import static com.loopers.support.E2ETestHelper.userAuthHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.loopers.interfaces.api.brand.v1.BrandDto;
import com.loopers.interfaces.api.brand.v1.BrandSteps;
import com.loopers.interfaces.api.product.v1.ProductDto;
import com.loopers.interfaces.api.product.v1.ProductSteps;
import com.loopers.interfaces.api.user.v1.UserV1Dto;
import com.loopers.support.BaseE2ETest;
import com.loopers.support.error.ErrorType;

class LikeV1ApiE2ETest extends BaseE2ETest {

    private HttpHeaders userHeaders;
    private Long productId;

    @BeforeEach
    void setUp() {
        // 사용자 생성
        var signUpRequest = new UserV1Dto.SignUpRequest(
                "testuser1", "Password1!", "홍길동", "1990-01-15", "test@example.com"
        );
        signUp(testRestTemplate, signUpRequest);
        userHeaders = userAuthHeaders(signUpRequest.loginId(), signUpRequest.password());

        // 브랜드 & 상품 생성
        var brandId = BrandSteps.createBrand(
                testRestTemplate, new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "브랜드 설명")
        );
        productId = ProductSteps.createProduct(
                testRestTemplate,
                new ProductDto.CreateProductRequest(brandId, "상품명", "https://example.com/thumb.png", 10000L, 100L, "상품 설명")
        );
    }

    @DisplayName("POST /api/v1/products/{productId}/likes")
    @Nested
    class LikeProduct {

        @DisplayName("인증된 사용자가 유효한 상품에 좋아요를 등록하면, 200 성공 응답을 받는다.")
        @Test
        void returnsSuccess_whenValidProductAndAuthenticated() {
            // act
            var response = likeProduct(testRestTemplate, productId, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().errorCode()).isNull()
            );
        }

        @DisplayName("이미 좋아요가 등록된 상품에 다시 요청하면, 200 성공 응답을 받고 likeCount는 증가하지 않는다. (멱등성)")
        @Test
        void returnsSuccessAndLikeCountUnchanged_whenLikeAlreadyExists() {
            // arrange
            likeProduct(testRestTemplate, productId, userHeaders);

            // act
            likeProduct(testRestTemplate, productId, userHeaders);

            // assert
            var productResponse = getActiveProduct(testRestTemplate, productId, userHeaders);
            assertThat(productResponse.getBody().data().likeCount()).isEqualTo(1L);
        }

        @DisplayName("존재하지 않는 상품이면, 404 PRODUCT_NOT_FOUND 에러 응답을 받는다.")
        @Test
        void returnsProductNotFound_whenProductDoesNotExist() {
            // act
            var response = likeProduct(testRestTemplate, 999L, userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.PRODUCT_NOT_FOUND);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoAuthHeader() {
            // act
            var response = likeProduct(testRestTemplate, productId, new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("DELETE /api/v1/products/{productId}/likes")
    @Nested
    class UnlikeProduct {

        @DisplayName("인증된 사용자가 좋아요를 취소하면, 200 성공 응답을 받는다.")
        @Test
        void returnsSuccess_whenValidProductAndAuthenticated() {
            // arrange
            likeProduct(testRestTemplate, productId, userHeaders);

            // act
            var response = unlikeProduct(testRestTemplate, productId, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().errorCode()).isNull()
            );
        }

        @DisplayName("좋아요가 존재하지 않는 상품에 취소 요청하면, 200 성공 응답을 받고 likeCount는 0을 유지한다. (멱등성)")
        @Test
        void returnsSuccessAndLikeCountUnchanged_whenLikeDoesNotExist() {
            // act
            unlikeProduct(testRestTemplate, productId, userHeaders);

            // assert
            var productResponse = getActiveProduct(testRestTemplate, productId, userHeaders);
            assertThat(productResponse.getBody().data().likeCount()).isZero();
        }

        @DisplayName("존재하지 않는 상품이면, 404 PRODUCT_NOT_FOUND 에러 응답을 받는다.")
        @Test
        void returnsProductNotFound_whenProductDoesNotExist() {
            // act
            var response = unlikeProduct(testRestTemplate, 999L, userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.PRODUCT_NOT_FOUND);
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoAuthHeader() {
            // act
            var response = unlikeProduct(testRestTemplate, productId, new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/users/me/likes")
    @Nested
    class ReadLikedProducts {

        private static final String LIKED_PRODUCTS_ENDPOINT = "/api/v1/users/me/likes";

        @DisplayName("인증된 사용자가 좋아요한 상품 목록을 조회하면, 200 성공 응답과 상품 목록을 받는다.")
        @Test
        void returnsLikedProducts_whenAuthenticated() {
            // arrange
            likeProduct(testRestTemplate, productId, userHeaders);

            // act
            var response = getLikedProducts(testRestTemplate, LIKED_PRODUCTS_ENDPOINT, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().hasNext()).isFalse()
            );
        }

        @DisplayName("좋아요한 상품이 없으면, 200 성공 응답과 빈 목록을 받는다.")
        @Test
        void returnsEmptyList_whenNoLikes() {
            // act
            var response = getLikedProducts(testRestTemplate, LIKED_PRODUCTS_ENDPOINT, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().hasNext()).isFalse()
            );
        }

        @DisplayName("인증 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoAuthHeader() {
            // act
            var response = getLikedProducts(testRestTemplate, LIKED_PRODUCTS_ENDPOINT, new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.UNAUTHORIZED, ErrorType.UNAUTHORIZED);
        }
    }
}

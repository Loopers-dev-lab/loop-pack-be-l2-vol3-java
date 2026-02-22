package com.loopers.interfaces.api.product.v1;

import static com.loopers.interfaces.api.like.v1.LikeSteps.likeProduct;
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
import com.loopers.interfaces.api.user.v1.UserV1Dto;
import com.loopers.support.BaseE2ETest;
import com.loopers.support.error.ErrorType;

class ProductV1ApiE2ETest extends BaseE2ETest {

    private Long brandId;
    private Long productId;

    @BeforeEach
    void setUp() {
        brandId = BrandSteps.createBrand(
                testRestTemplate,
                new BrandDto.CreateBrandRequest("테스트브랜드", "https://example.com/logo.png", "브랜드 설명")
        );
        var productResponse = ProductSteps.createProduct(
                testRestTemplate,
                new ProductDto.CreateProductRequest(brandId, "테스트상품", "https://example.com/thumb.png", 10000L, 100L, "상품 설명")
        );
        productId = productResponse.getBody().data().productId();
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetActiveProduct {

        @DisplayName("비로그인 사용자가 상품을 조회하면, 상품 상세 정보와 liked=false를 반환한다.")
        @Test
        void returnsProductDetail_whenNotAuthenticated() {
            // act
            var response = getActiveProduct(testRestTemplate, productId);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().productId()).isEqualTo(productId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("테스트상품"),
                    () -> assertThat(response.getBody().data().thumbnailUrl()).isEqualTo("https://example.com/thumb.png"),
                    () -> assertThat(response.getBody().data().price()).isEqualTo(10000L),
                    () -> assertThat(response.getBody().data().stock()).isEqualTo(100L),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("상품 설명"),
                    () -> assertThat(response.getBody().data().brand().id()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().brand().name()).isEqualTo("테스트브랜드"),
                    () -> assertThat(response.getBody().data().brand().logoUrl()).isEqualTo("https://example.com/logo.png"),
                    () -> assertThat(response.getBody().data().likeCount()).isZero(),
                    () -> assertThat(response.getBody().data().liked()).isFalse()
            );
        }

        @DisplayName("로그인 사용자가 좋아요한 상품을 조회하면, liked=true를 반환한다.")
        @Test
        void returnsLikedTrue_whenUserLikedProduct() {
            // arrange
            var userHeaders = createUserAndGetHeaders("user1", "Password1!", "홍길동", "1990-01-15", "user1@test.com");
            likeProduct(testRestTemplate, productId, userHeaders);

            // act
            var response = getActiveProduct(testRestTemplate, productId, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().liked()).isTrue(),
                    () -> assertThat(response.getBody().data().likeCount()).isEqualTo(1L)
            );
        }

        @DisplayName("로그인 사용자가 좋아요하지 않은 상품을 조회하면, liked=false를 반환한다.")
        @Test
        void returnsLikedFalse_whenUserDidNotLikeProduct() {
            // arrange
            var userHeaders = createUserAndGetHeaders("user1", "Password1!", "홍길동", "1990-01-15", "user1@test.com");

            // act
            var response = getActiveProduct(testRestTemplate, productId, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().liked()).isFalse(),
                    () -> assertThat(response.getBody().data().likeCount()).isZero()
            );
        }

        @DisplayName("좋아요가 여러 개인 상품을 조회하면, 정확한 likeCount를 반환한다.")
        @Test
        void returnsCorrectLikeCount_whenMultipleUsersLiked() {
            // arrange
            var user1Headers = createUserAndGetHeaders("user1", "Password1!", "홍길동", "1990-01-15", "user1@test.com");
            var user2Headers = createUserAndGetHeaders("user2", "Password1!", "김철수", "1991-02-20", "user2@test.com");
            likeProduct(testRestTemplate, productId, user1Headers);
            likeProduct(testRestTemplate, productId, user2Headers);

            // act
            var response = getActiveProduct(testRestTemplate, productId);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().likeCount()).isEqualTo(2L),
                    () -> assertThat(response.getBody().data().liked()).isFalse()
            );
        }

        @DisplayName("존재하지 않는 상품을 조회하면, 404 PRODUCT_NOT_FOUND를 반환한다.")
        @Test
        void returnsProductNotFound_whenProductDoesNotExist() {
            // act
            var response = getActiveProduct(testRestTemplate, 999L);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.PRODUCT_NOT_FOUND);
        }

        @DisplayName("삭제된 상품을 조회하면, 404 PRODUCT_NOT_FOUND를 반환한다.")
        @Test
        void returnsProductNotFound_whenProductIsDeleted() {
            // arrange
            ProductSteps.deleteProduct(testRestTemplate, productId);

            // act
            var response = getActiveProduct(testRestTemplate, productId);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.PRODUCT_NOT_FOUND);
        }
    }

    private HttpHeaders createUserAndGetHeaders(String loginId, String password, String name, String birthDate, String email) {
        signUp(testRestTemplate, new UserV1Dto.SignUpRequest(loginId, password, name, birthDate, email));
        return userAuthHeaders(loginId, password);
    }
}
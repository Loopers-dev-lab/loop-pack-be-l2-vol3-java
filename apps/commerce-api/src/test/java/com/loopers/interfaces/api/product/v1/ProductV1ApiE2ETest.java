package com.loopers.interfaces.api.product.v1;

import static com.loopers.interfaces.api.like.v1.LikeSteps.likeProduct;
import static com.loopers.interfaces.api.product.v1.ProductSteps.getActiveProduct;
import static com.loopers.interfaces.api.product.v1.ProductSteps.getActiveProducts;
import static com.loopers.interfaces.api.user.v1.UserSteps.signUp;
import static com.loopers.support.E2ETestHelper.assertErrorResponse;
import static com.loopers.support.E2ETestHelper.userAuthHeaders;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
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
                new BrandDto.CreateBrandRequest("테스트 브랜드", "https://example.com/logo.png", "브랜드 설명")
        );
        productId = ProductSteps.createProduct(
                testRestTemplate,
                new ProductDto.CreateProductRequest(
                        brandId,
                        "테스트 상품",
                        "https://example.com/thumb.png",
                        10000L,
                        100L,
                        "상품 설명"
                )
        );
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class ReadActiveProducts {

        @DisplayName("brandId가 전달된 경우, 해당 브랜드의 활성 상품만 필터링하여 반환한다.")
        @Test
        void returnsFilteredProducts_whenBrandIdIsProvided() {
            // arrange
            var brandId2 = BrandSteps.createBrand(
                    testRestTemplate,
                    new BrandDto.CreateBrandRequest("다른 브랜드", "https://example.com/logo2.png", null)
            );
            ProductSteps.createProduct(
                    testRestTemplate,
                    new ProductDto.CreateProductRequest(
                            brandId2,
                            "다른 브랜드 상품",
                            "https://example.com/thumb2.png",
                            20000L,
                            50L,
                            null
                    )
            );
            ProductSteps.createProduct(
                    testRestTemplate,
                    new ProductDto.CreateProductRequest(
                            brandId,
                            "같은 브랜드 상품",
                            "https://example.com/thumb3.png",
                            30000L,
                            200L,
                            null
                    )
            );

            // act
            var response = getActiveProducts(testRestTemplate, "brandId=" + brandId, new HttpHeaders());

            // assert
            var content = response.getBody().data().content();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(content).hasSize(2),
                    () -> assertThat(content).allSatisfy(p ->
                            assertThat(p.brand().id()).isEqualTo(brandId)
                    )
            );
        }

        @DisplayName("인증된 사용자가 상품 목록을 조회하면, 좋아요한 상품은 liked=true, 나머지는 liked=false를 반환한다.")
        @Test
        void returnsLikedStatus_whenAuthenticatedUserRequestsProductList() {
            // arrange
            var product2Id = ProductSteps.createProduct(
                    testRestTemplate,
                    new ProductDto.CreateProductRequest(brandId, "두 번째 상품", "https://example.com/thumb2.png", 20000L, 50L, null)
            );

            signUp(testRestTemplate, new UserV1Dto.SignUpRequest("user1", "Password1!", "홍길동", "1990-01-15", "user1@test.com"));
            var userHeaders = userAuthHeaders("user1", "Password1!");
            likeProduct(testRestTemplate, productId, userHeaders);

            // act
            var response = getActiveProducts(testRestTemplate, "", userHeaders);

            // assert
            var content = response.getBody().data().content();
            var likedProduct = content.stream().filter(p -> p.productId().equals(productId)).findFirst().get();
            var notLikedProduct = content.stream().filter(p -> p.productId().equals(product2Id)).findFirst().get();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(content).hasSize(2),
                    () -> assertThat(likedProduct.liked()).isTrue(),
                    () -> assertThat(likedProduct.likeCount()).isEqualTo(1L),
                    () -> assertThat(notLikedProduct.liked()).isFalse(),
                    () -> assertThat(notLikedProduct.likeCount()).isZero()
            );
        }

        @DisplayName("존재하지 않는 브랜드로 필터링하면, 404 BRAND_NOT_FOUND를 반환한다.")
        @Test
        void returnsBrandNotFound_whenBrandDoesNotExist() {
            // act
            var response = getActiveProducts(testRestTemplate, "brandId=999", new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.BRAND_NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드로 필터링하면, 404 BRAND_NOT_FOUND를 반환한다.")
        @Test
        void returnsBrandNotFound_whenBrandIsDeleted() {
            // arrange
            var deletedBrandId = BrandSteps.createBrand(
                    testRestTemplate,
                    new BrandDto.CreateBrandRequest("삭제 브랜드", "https://example.com/logo2.png", null)
            );
            BrandSteps.deleteBrand(testRestTemplate, deletedBrandId);

            // act
            var response = getActiveProducts(testRestTemplate, "brandId=" + deletedBrandId, new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.BRAND_NOT_FOUND);
        }

        @DisplayName("sort=LIKE_COUNT_DESC로 조회하면, 좋아요 수 내림차순으로 정렬된 상품을 반환한다.")
        @Test
        void returnsProductsSortedByLikeCountDesc_whenSortIsLikeCountDesc() {
            // arrange
            var product2Id = ProductSteps.createProduct(
                    testRestTemplate,
                    new ProductDto.CreateProductRequest(
                            brandId,
                            "인기 상품",
                            "https://example.com/thumb2.png",
                            20000L,
                            50L,
                            null
                    )
            );
            var product3Id = ProductSteps.createProduct(
                    testRestTemplate,
                    new ProductDto.CreateProductRequest(
                            brandId,
                            "보통 상품",
                            "https://example.com/thumb3.png",
                            15000L,
                            30L,
                            null
                    )
            );

            // product2에 좋아요 2개, product1에 좋아요 1개, product3에 좋아요 0개
            signUp(testRestTemplate, new UserV1Dto.SignUpRequest("user1", "Password1!", "홍길동", "1990-01-15", "user1@test.com"));
            signUp(testRestTemplate, new UserV1Dto.SignUpRequest("user2", "Password1!", "김철수", "1991-02-20", "user2@test.com"));
            var user1Headers = userAuthHeaders("user1", "Password1!");
            var user2Headers = userAuthHeaders("user2", "Password1!");
            likeProduct(testRestTemplate, product2Id, user1Headers);
            likeProduct(testRestTemplate, product2Id, user2Headers);
            likeProduct(testRestTemplate, productId, user1Headers);

            // act & assert — 비동기 이벤트 처리 대기
            await().atMost(5, SECONDS).untilAsserted(() -> {
                var response = getActiveProducts(testRestTemplate, "sort=LIKE_COUNT_DESC", new HttpHeaders());
                var content = response.getBody().data().content();
                assertAll(
                        () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                        () -> assertThat(content).hasSize(3),
                        () -> assertThat(content.get(0).productId()).isEqualTo(product2Id),
                        () -> assertThat(content.get(0).likeCount()).isEqualTo(2L),
                        () -> assertThat(content.get(1).productId()).isEqualTo(productId),
                        () -> assertThat(content.get(1).likeCount()).isEqualTo(1L),
                        () -> assertThat(content.get(2).productId()).isEqualTo(product3Id),
                        () -> assertThat(content.get(2).likeCount()).isZero()
                );
            });
        }

        @DisplayName("sort=PRICE_ASC로 조회하면, 가격 오름차순으로 정렬된 상품을 반환한다.")
        @Test
        void returnsProductsSortedByPriceAsc_whenSortIsPriceAsc() {
            // arrange
            ProductSteps.createProduct(
                    testRestTemplate,
                    new ProductDto.CreateProductRequest(
                            brandId,
                            "비싼 상품",
                            "https://example.com/thumb2.png",
                            50000L,
                            10L,
                            null
                    )
            );
            ProductSteps.createProduct(
                    testRestTemplate,
                    new ProductDto.CreateProductRequest(
                            brandId,
                            "싼 상품",
                            "https://example.com/thumb3.png",
                            3000L,
                            200L,
                            null
                    )
            );

            // act
            var response = getActiveProducts(testRestTemplate, "sort=PRICE_ASC", new HttpHeaders());

            // assert
            var content = response.getBody().data().content();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(content).hasSize(3),
                    () -> assertThat(content.get(0).price()).isEqualTo(3000L),
                    () -> assertThat(content.get(1).price()).isEqualTo(10000L),
                    () -> assertThat(content.get(2).price()).isEqualTo(50000L)
            );
        }

        @DisplayName("유효하지 않은 정렬 옵션을 전달하면, 400 INVALID_SORT_TYPE을 반환한다.")
        @Test
        void returnsInvalidSortType_whenSortIsInvalid() {
            // act
            var response = getActiveProducts(testRestTemplate, "sort=INVALID", new HttpHeaders());

            // assert
            assertErrorResponse(response, HttpStatus.BAD_REQUEST, ErrorType.INVALID_SORT_TYPE);
        }

        @DisplayName("활성 상태의 상품을 페이지 단위로 조회하면, 삭제된 상품과 삭제된 브랜드의 상품은 제외하고 등록일 내림차순으로 반환한다.")
        @Test
        void returnsActiveProducts_excludingDeletedProductsAndDeletedBrandProducts() {
            // arrange
            var brandId2 = BrandSteps.createBrand(
                    testRestTemplate,
                    new BrandDto.CreateBrandRequest("삭제 브랜드", "https://example.com/logo2.png", null)
            );
            ProductSteps.createProduct(
                    testRestTemplate,
                    new ProductDto.CreateProductRequest(
                            brandId2,
                            "삭제 브랜드 상품",
                            "https://example.com/thumb2.png", 
                            20000L, 
                            50L, 
                            null
                    )
            );
            var secondProductId = ProductSteps.createProduct(
                    testRestTemplate,
                    new ProductDto.CreateProductRequest(brandId,
                            "두 번째 상품",
                            "https://example.com/thumb3.png",
                            30000L,
                            200L,
                            "설명 2"
                    )
            );
            var deletedProductId = ProductSteps.createProduct(
                    testRestTemplate,
                    new ProductDto.CreateProductRequest(
                            brandId,
                            "삭제 상품",
                            "https://example.com/thumb4.png",
                            5000L,
                            10L,
                            null
                    )
            );

            ProductSteps.deleteProduct(testRestTemplate, deletedProductId);
            BrandSteps.deleteBrand(testRestTemplate, brandId2);

            // act
            var response = getActiveProducts(testRestTemplate, "", new HttpHeaders());

            // assert
            var content = response.getBody().data().content();
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(content).hasSize(2),
                    () -> assertThat(content.get(0).productId()).isEqualTo(secondProductId),
                    () -> assertThat(content.get(1).productId()).isEqualTo(productId),
                    () -> assertThat(content.get(0).brand().id()).isEqualTo(brandId),
                    () -> assertThat(content.get(0).likeCount()).isZero(),
                    () -> assertThat(content.get(0).liked()).isFalse()
            );
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class ReadActiveProductDetail {

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
                    () -> assertThat(response.getBody().data().name()).isEqualTo("테스트 상품"),
                    () -> assertThat(response.getBody().data().thumbnailUrl()).isEqualTo("https://example.com/thumb.png"),
                    () -> assertThat(response.getBody().data().price()).isEqualTo(10000L),
                    () -> assertThat(response.getBody().data().stock()).isEqualTo(100L),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("상품 설명"),
                    () -> assertThat(response.getBody().data().brand().id()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().brand().name()).isEqualTo("테스트 브랜드"),
                    () -> assertThat(response.getBody().data().brand().logoUrl()).isEqualTo("https://example.com/logo.png"),
                    () -> assertThat(response.getBody().data().likeCount()).isZero(),
                    () -> assertThat(response.getBody().data().liked()).isFalse()
            );
        }

        @DisplayName("로그인 사용자가 좋아요한 상품을 조회하면, liked=true를 반환한다.")
        @Test
        void returnsLikedTrue_whenUserLikedProduct() {
            // arrange
            signUp(testRestTemplate, new UserV1Dto.SignUpRequest("user1", "Password1!", "홍길동", "1990-01-15", "user1@test.com"));
            var userHeaders = userAuthHeaders("user1", "Password1!");
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
            signUp(testRestTemplate, new UserV1Dto.SignUpRequest("user1", "Password1!", "홍길동", "1990-01-15", "user1@test.com"));
            var userHeaders = userAuthHeaders("user1", "Password1!");

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
            signUp(testRestTemplate, new UserV1Dto.SignUpRequest("user1", "Password1!", "홍길동", "1990-01-15", "user1@test.com"));
            signUp(testRestTemplate, new UserV1Dto.SignUpRequest("user2", "Password1!", "김철수", "1991-02-20", "user2@test.com"));
            var user1Headers = userAuthHeaders("user1", "Password1!");
            var user2Headers = userAuthHeaders("user2", "Password1!");
            likeProduct(testRestTemplate, productId, user1Headers);
            likeProduct(testRestTemplate, productId, user2Headers);

            // act & assert — 비동기 이벤트 처리 대기
            await().atMost(5, SECONDS).untilAsserted(() -> {
                var response = getActiveProduct(testRestTemplate, productId);
                assertAll(
                        () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                        () -> assertThat(response.getBody().data().likeCount()).isEqualTo(2L),
                        () -> assertThat(response.getBody().data().liked()).isFalse()
                );
            });
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
}

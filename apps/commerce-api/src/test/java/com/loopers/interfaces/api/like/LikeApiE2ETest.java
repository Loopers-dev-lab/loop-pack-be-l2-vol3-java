package com.loopers.interfaces.api.like;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.product.ProductAdminV1Dto;
import com.loopers.support.E2ETestFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2ETestFixture.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class LikeApiE2ETest {

    private static final String LIKE_ENDPOINT = "/api/v1/products/{productId}/likes";
    private static final String LIKE_LIST_ENDPOINT = "/api/v1/likes";

    private static final String LOGIN_ID = "testuser";
    private static final String LOGIN_PW = "Test1234!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private E2ETestFixture fixture;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 좋아요_등록 {

        @Test
        void 활성_상품에_좋아요를_등록하면_200_응답() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            ResponseEntity<ApiResponse<Void>> response = postLike(productId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 좋아요_등록_시_해당_상품의_좋아요_수가_1_증가한다() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            ResponseEntity<ApiResponse<Void>> response = postLike(productId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> productResponse =
                    getProductList("?status=ACTIVE");
            assertThat(productResponse.getBody().data().content().get(0).likeCount()).isEqualTo(1);
        }

        @Test
        void 이미_좋아요한_상품에_재요청하면_200_응답하고_좋아요_수가_변동되지_않는다() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            postLike(productId);
            ResponseEntity<ApiResponse<Void>> response = postLike(productId);

            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> productResponse =
                    getProductList("?status=ACTIVE");
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(productResponse.getBody().data().content().get(0).likeCount()).isEqualTo(1)
            );
        }

        @Test
        void 삭제된_상품에_좋아요_등록하면_404_응답() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            fixture.deleteProduct(productId);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKE_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {},
                    productId
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 상품입니다")
            );
        }

        @Test
        void 미존재_상품에_좋아요_등록하면_404_응답() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKE_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {},
                    999L
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 상품입니다")
            );
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKE_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {},
                    1L
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "notexist");
            headers.set("X-Loopers-LoginPw", "WrongPass1!");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKE_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {},
                    1L
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    @Nested
    class 좋아요_취소 {

        @Test
        void 좋아요한_상품의_좋아요를_취소하면_200_응답() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            postLike(productId);

            ResponseEntity<ApiResponse<Void>> response = deleteLike(productId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 좋아요_취소_시_해당_상품의_좋아요_수가_1_감소한다() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            postLike(productId);

            ResponseEntity<ApiResponse<Void>> response = deleteLike(productId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> productResponse =
                    getProductList("?status=ACTIVE");
            assertThat(productResponse.getBody().data().content().get(0).likeCount()).isEqualTo(0);
        }

        @Test
        void 좋아요하지_않은_상품에_취소_요청하면_200_응답하고_좋아요_수가_변동되지_않는다() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            ResponseEntity<ApiResponse<Void>> response = deleteLike(productId);

            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> productResponse =
                    getProductList("?status=ACTIVE");
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(productResponse.getBody().data().content().get(0).likeCount()).isEqualTo(0)
            );
        }

        @Test
        void 삭제된_상품에_좋아요_취소하면_200_응답하고_좋아요가_존재하면_삭제하고_likeCount를_감소한다() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            postLike(productId);
            fixture.deleteProduct(productId);

            ResponseEntity<ApiResponse<Void>> response = deleteLike(productId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> {
                        ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> productResponse =
                                getProductList("?status=DELETED");
                        assertThat(productResponse.getBody().data().content().get(0).likeCount()).isEqualTo(0);
                    }
            );
        }

        @Test
        void 미존재_상품에_좋아요_취소하면_200_응답하고_상태_유지() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");

            ResponseEntity<ApiResponse<Void>> response = deleteLike(999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKE_ENDPOINT, HttpMethod.DELETE,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {},
                    1L
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "notexist");
            headers.set("X-Loopers-LoginPw", "WrongPass1!");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKE_ENDPOINT, HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {},
                    1L
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    @Nested
    class 좋아요_목록_조회 {

        @Test
        void 좋아요한_상품_목록을_좋아요_등록순으로_페이징하여_200_응답() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId1 = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long productId2 = fixture.registerProduct(brandId, "슬리퍼", new BigDecimal("30000"), 50, "편한 슬리퍼");
            postLike(productId1);
            postLike(productId2);

            ResponseEntity<ApiResponse<PageResponse<LikeV1Dto.LikeProductResponse>>> response = getLikeList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("슬리퍼"),
                    () -> assertThat(response.getBody().data().content().get(1).name()).isEqualTo("운동화"),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().content().get(0).brandName()).isEqualTo("나이키")
            );
        }

        @Test
        void 활성_상품만_반환한다() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId1 = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            Long productId2 = fixture.registerProduct(brandId, "슬리퍼", new BigDecimal("30000"), 50, "편한 슬리퍼");
            postLike(productId1);
            postLike(productId2);
            fixture.deleteProduct(productId2);

            ResponseEntity<ApiResponse<PageResponse<LikeV1Dto.LikeProductResponse>>> response = getLikeList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("운동화"),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(1)
            );
        }

        @Test
        void 결과가_없으면_빈_목록을_반환한다() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");

            ResponseEntity<ApiResponse<PageResponse<LikeV1Dto.LikeProductResponse>>> response = getLikeList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isZero()
            );
        }

        @Test
        void 요청_필드_규칙_위반_시_400_응답() {
            fixture.signUp(LOGIN_ID, LOGIN_PW, "홍길동", "test@example.com");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKE_LIST_ENDPOINT + "?page=-1", HttpMethod.GET,
                    new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKE_LIST_ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "notexist");
            headers.set("X-Loopers-LoginPw", "WrongPass1!");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKE_LIST_ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    // --- 헬퍼 메서드 ---

    private ResponseEntity<ApiResponse<Void>> postLike(Long productId) {
        return testRestTemplate.exchange(
                LIKE_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<>() {},
                productId
        );
    }

    private ResponseEntity<ApiResponse<Void>> deleteLike(Long productId) {
        return testRestTemplate.exchange(
                LIKE_ENDPOINT, HttpMethod.DELETE,
                new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<>() {},
                productId
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<LikeV1Dto.LikeProductResponse>>> getLikeList(String queryString) {
        return testRestTemplate.exchange(
                LIKE_LIST_ENDPOINT + queryString, HttpMethod.GET,
                new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> getProductList(String queryString) {
        return testRestTemplate.exchange(
                "/api-admin/v1/products" + queryString, HttpMethod.GET,
                new HttpEntity<>(fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private HttpHeaders userHeaders() {
        return fixture.userHeaders(LOGIN_ID, LOGIN_PW);
    }
}

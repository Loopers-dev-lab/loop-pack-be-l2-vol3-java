package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.support.E2ETestFixture;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductUserApiE2ETest {

    private static final String ENDPOINT = "/api/v1/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private E2ETestFixture fixture;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 상품_목록_조회 {

        @Test
        void 조건_없이_조회하면_활성_상품만_최신순으로_페이징하여_200_응답한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            fixture.registerProduct(brandId, "운동화A", new BigDecimal("10000"), 10, "설명A");
            fixture.registerProduct(brandId, "운동화B", new BigDecimal("20000"), 20, "설명B");
            fixture.registerProduct(brandId, "운동화C", new BigDecimal("30000"), 30, "설명C");

            ResponseEntity<ApiResponse<PageResponse<ProductUserV1Dto.ProductResponse>>> response = getList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(3),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("운동화C"),
                    () -> assertThat(response.getBody().data().content().get(1).name()).isEqualTo("운동화B"),
                    () -> assertThat(response.getBody().data().content().get(2).name()).isEqualTo("운동화A"),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(3),
                    () -> assertThat(response.getBody().data().page()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().size()).isEqualTo(20)
            );
        }

        @Test
        void 삭제된_상품은_반환하지_않는다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            fixture.registerProduct(brandId, "운동화A", new BigDecimal("10000"), 10, "설명A");
            Long deletedProductId = fixture.registerProduct(brandId, "운동화B", new BigDecimal("20000"), 20, "설명B");
            fixture.deleteProduct(deletedProductId);

            ResponseEntity<ApiResponse<PageResponse<ProductUserV1Dto.ProductResponse>>> response = getList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("운동화A")
            );
        }

        @Test
        void brandId로_필터링하면_해당_브랜드에_속한_활성_상품만_반환한다() {
            Long nikeId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long adidasId = fixture.registerBrand("아디다스", "독일 브랜드");
            fixture.registerProduct(nikeId, "나이키 운동화", new BigDecimal("10000"), 10, "설명");
            fixture.registerProduct(adidasId, "아디다스 운동화", new BigDecimal("20000"), 20, "설명");
            fixture.registerProduct(nikeId, "나이키 런닝화", new BigDecimal("30000"), 30, "설명");

            ResponseEntity<ApiResponse<PageResponse<ProductUserV1Dto.ProductResponse>>> response =
                    getList("?brandId=" + nikeId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(ProductUserV1Dto.ProductResponse::brandId)
                            .containsOnly(nikeId)
            );
        }

        @Test
        void sort_RECENT이면_최신_등록순으로_정렬한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            fixture.registerProduct(brandId, "운동화A", new BigDecimal("10000"), 10, "설명A");
            fixture.registerProduct(brandId, "운동화B", new BigDecimal("20000"), 20, "설명B");
            fixture.registerProduct(brandId, "운동화C", new BigDecimal("30000"), 30, "설명C");

            ResponseEntity<ApiResponse<PageResponse<ProductUserV1Dto.ProductResponse>>> response =
                    getList("?sort=RECENT");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(3),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(ProductUserV1Dto.ProductResponse::name)
                            .containsExactly("운동화C", "운동화B", "운동화A")
            );
        }

        @Test
        void sort_PRICE_ASC이면_가격_오름차순으로_정렬한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            fixture.registerProduct(brandId, "비싼 운동화", new BigDecimal("90000"), 10, "설명");
            fixture.registerProduct(brandId, "싼 운동화", new BigDecimal("10000"), 10, "설명");
            fixture.registerProduct(brandId, "중간 운동화", new BigDecimal("50000"), 10, "설명");

            ResponseEntity<ApiResponse<PageResponse<ProductUserV1Dto.ProductResponse>>> response =
                    getList("?sort=PRICE_ASC");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(ProductUserV1Dto.ProductResponse::name)
                            .containsExactly("싼 운동화", "중간 운동화", "비싼 운동화")
            );
        }

        @Test
        void sort_LIKES_DESC이면_좋아요_내림차순으로_정렬한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long p1 = fixture.registerProduct(brandId, "인기 상품", new BigDecimal("10000"), 10, "설명");
            fixture.registerProduct(brandId, "보통 상품", new BigDecimal("20000"), 20, "설명");
            Long p3 = fixture.registerProduct(brandId, "최고 인기", new BigDecimal("30000"), 30, "설명");

            fixture.signUp("user1", "Pass1234!", "홍길동", "user1@example.com");
            fixture.signUp("user2", "Pass1234!", "홍길동", "user2@example.com");
            fixture.signUp("user3", "Pass1234!", "홍길동", "user3@example.com");

            likeProduct(p1, "user1", "Pass1234!");
            likeProduct(p1, "user2", "Pass1234!");
            likeProduct(p3, "user1", "Pass1234!");
            likeProduct(p3, "user2", "Pass1234!");
            likeProduct(p3, "user3", "Pass1234!");

            ResponseEntity<ApiResponse<PageResponse<ProductUserV1Dto.ProductResponse>>> response =
                    getList("?sort=LIKES_DESC");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(ProductUserV1Dto.ProductResponse::name)
                            .containsExactly("최고 인기", "인기 상품", "보통 상품")
            );
        }

        @Test
        void sort를_지정하지_않으면_기본값_RECENT로_정렬한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            fixture.registerProduct(brandId, "운동화A", new BigDecimal("10000"), 10, "설명A");
            fixture.registerProduct(brandId, "운동화B", new BigDecimal("20000"), 20, "설명B");

            ResponseEntity<ApiResponse<PageResponse<ProductUserV1Dto.ProductResponse>>> response = getList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(ProductUserV1Dto.ProductResponse::name)
                            .containsExactly("운동화B", "운동화A")
            );
        }

        @Test
        void sort에_잘못된_값을_보내면_400_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?sort=INVALID", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 결과가_없으면_빈_목록을_반환한다() {
            ResponseEntity<ApiResponse<PageResponse<ProductUserV1Dto.ProductResponse>>> response =
                    getList("?brandId=999");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0)
            );
        }

        @Test
        void 요청_필드_규칙_위반_시_400_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?page=-1", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class 상품_상세_조회 {

        @Test
        void 활성_상품을_조회하면_200_응답과_상품_상세_정보를_반환한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            ResponseEntity<ApiResponse<ProductUserV1Dto.ProductResponse>> response = getDetail(productId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(productId),
                    () -> assertThat(response.getBody().data().brandId()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("운동화"),
                    () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(new BigDecimal("50000")),
                    () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(100),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("편한 운동화"),
                    () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().updatedAt()).isNotNull()
            );
        }

        @Test
        void 삭제된_상품을_조회하면_404_응답() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            fixture.deleteProduct(productId);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + productId, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 상품입니다")
            );
        }

        @Test
        void 해당_ID의_상품_데이터가_존재하지_않으면_404_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 상품입니다")
            );
        }
    }

    // --- 헬퍼 메서드 ---

    private void likeProduct(Long productId, String loginId, String password) {
        HttpHeaders headers = fixture.userHeaders(loginId, password);
        testRestTemplate.exchange(
                "/api/v1/products/" + productId + "/likes", HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<ProductUserV1Dto.ProductResponse>>> getList(String queryString) {
        return testRestTemplate.exchange(
                ENDPOINT + queryString, HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<ProductUserV1Dto.ProductResponse>> getDetail(Long productId) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + productId, HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

}

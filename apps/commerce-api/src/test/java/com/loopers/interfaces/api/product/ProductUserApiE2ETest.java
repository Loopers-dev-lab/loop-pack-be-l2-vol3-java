package com.loopers.interfaces.api.product;

import com.loopers.application.brand.BrandRequest;
import com.loopers.application.product.ProductRequest;
import com.loopers.application.user.UserRequest;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.brand.BrandAdminV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
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
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ProductUserApiE2ETest {

    private static final String ENDPOINT = "/api/v1/products";
    private static final String ADMIN_PRODUCT_ENDPOINT = "/api-admin/v1/products";
    private static final String ADMIN_BRAND_ENDPOINT = "/api-admin/v1/brands";
    private static final String VALID_LDAP = "admin-ldap";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 상품_목록_조회 {

        @Test
        void 조건_없이_조회하면_활성_상품만_최신순으로_페이징하여_200_응답한다() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            registerProduct(brandId, "운동화A", new BigDecimal("10000"), 10, "설명A");
            registerProduct(brandId, "운동화B", new BigDecimal("20000"), 20, "설명B");
            registerProduct(brandId, "운동화C", new BigDecimal("30000"), 30, "설명C");

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
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            registerProduct(brandId, "운동화A", new BigDecimal("10000"), 10, "설명A");
            Long deletedProductId = registerProduct(brandId, "운동화B", new BigDecimal("20000"), 20, "설명B");
            deleteProduct(deletedProductId);

            ResponseEntity<ApiResponse<PageResponse<ProductUserV1Dto.ProductResponse>>> response = getList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("운동화A")
            );
        }

        @Test
        void brandId로_필터링하면_해당_브랜드에_속한_활성_상품만_반환한다() {
            Long nikeId = registerBrand("나이키", "스포츠 브랜드");
            Long adidasId = registerBrand("아디다스", "독일 브랜드");
            registerProduct(nikeId, "나이키 운동화", new BigDecimal("10000"), 10, "설명");
            registerProduct(adidasId, "아디다스 운동화", new BigDecimal("20000"), 20, "설명");
            registerProduct(nikeId, "나이키 런닝화", new BigDecimal("30000"), 30, "설명");

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
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            registerProduct(brandId, "운동화A", new BigDecimal("10000"), 10, "설명A");
            registerProduct(brandId, "운동화B", new BigDecimal("20000"), 20, "설명B");
            registerProduct(brandId, "운동화C", new BigDecimal("30000"), 30, "설명C");

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
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            registerProduct(brandId, "비싼 운동화", new BigDecimal("90000"), 10, "설명");
            registerProduct(brandId, "싼 운동화", new BigDecimal("10000"), 10, "설명");
            registerProduct(brandId, "중간 운동화", new BigDecimal("50000"), 10, "설명");

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
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long p1 = registerProduct(brandId, "인기 상품", new BigDecimal("10000"), 10, "설명");
            registerProduct(brandId, "보통 상품", new BigDecimal("20000"), 20, "설명");
            Long p3 = registerProduct(brandId, "최고 인기", new BigDecimal("30000"), 30, "설명");

            signUpUser("user1", "Pass1234!");
            signUpUser("user2", "Pass1234!");
            signUpUser("user3", "Pass1234!");

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
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            registerProduct(brandId, "운동화A", new BigDecimal("10000"), 10, "설명A");
            registerProduct(brandId, "운동화B", new BigDecimal("20000"), 20, "설명B");

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
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

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
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            Long productId = registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            deleteProduct(productId);

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

    private Long registerBrand(String name, String description) {
        BrandRequest.Register request = new BrandRequest.Register(name, description);
        ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ADMIN_BRAND_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private Long registerProduct(Long brandId, String name, BigDecimal price, Integer stockQuantity, String description) {
        ProductRequest.Register request = new ProductRequest.Register(
                brandId, name, price, stockQuantity, description
        );
        ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                ADMIN_PRODUCT_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private void deleteProduct(Long productId) {
        testRestTemplate.exchange(
                ADMIN_PRODUCT_ENDPOINT + "/" + productId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<ApiResponse<Void>>() {}
        );
    }

    private void signUpUser(String loginId, String password) {
        UserRequest.SignUp request = new UserRequest.SignUp(
                loginId, password, "홍길동",
                LocalDate.of(2000, 1, 15), loginId + "@example.com"
        );
        testRestTemplate.exchange(
                "/api/v1/users", HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {}
        );
    }

    private void likeProduct(Long productId, String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        headers.set("X-Loopers-LoginPw", password);
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

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", VALID_LDAP);
        return headers;
    }

}

package com.loopers.interfaces.api.product;

import com.loopers.application.product.ProductRequest;
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
class ProductAdminApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/products";

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
    class 상품_등록 {

        @Test
        void 유효한_정보로_등록하면_상품_정보가_반환된다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            ProductRequest.Register request = new ProductRequest.Register(
                    brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화"
            );

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = postRegister(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().brandId()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("운동화"),
                    () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(new BigDecimal("50000")),
                    () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(100),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("편한 운동화"),
                    () -> assertThat(response.getBody().data().likeCount()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("ACTIVE"),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().updatedAt()).isNotNull()
            );
        }

        @Test
        void 미존재_브랜드면_404_응답() {
            ProductRequest.Register request = new ProductRequest.Register(
                    999L, "운동화", new BigDecimal("50000"), 100, "설명"
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 브랜드입니다")
            );
        }

        @Test
        void 삭제된_브랜드면_404_응답() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            fixture.deleteBrand(brandId);

            ProductRequest.Register request = new ProductRequest.Register(
                    brandId, "운동화", new BigDecimal("50000"), 100, "설명"
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 요청_필드_규칙_위반_시_400_응답() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            ProductRequest.Register request = new ProductRequest.Register(
                    brandId, "", new BigDecimal("50000"), 100, "설명"
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ProductRequest.Register request = new ProductRequest.Register(
                    1L, "운동화", new BigDecimal("50000"), 100, "설명"
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            ProductRequest.Register request = new ProductRequest.Register(
                    1L, "운동화", new BigDecimal("50000"), 100, "설명"
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    @Nested
    class 상품_수정 {

        @Test
        void 유효한_정보로_수정하면_200_응답과_수정된_정보를_반환한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            ProductRequest.Update request = new ProductRequest.Update(
                    "런닝화", new BigDecimal("60000"), 200, "가벼운 런닝화"
            );

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = patchUpdate(productId, request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(productId),
                    () -> assertThat(response.getBody().data().brandId()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("런닝화"),
                    () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(new BigDecimal("60000")),
                    () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(200),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("가벼운 런닝화")
            );
        }

        @Test
        void 상품명만_보내면_상품명만_수정된다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            ProductRequest.Update request = new ProductRequest.Update(
                    "런닝화", null, null, null
            );

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = patchUpdate(productId, request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("런닝화"),
                    () -> assertThat(response.getBody().data().price()).isEqualByComparingTo(new BigDecimal("50000")),
                    () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(100),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("편한 운동화")
            );
        }

        @Test
        void 소속_브랜드는_변경되지_않는다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            ProductRequest.Update request = new ProductRequest.Update(
                    "런닝화", null, null, null
            );

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = patchUpdate(productId, request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().brandId()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키")
            );
        }

        @Test
        void 미존재_상품이면_404_응답() {
            ProductRequest.Update request = new ProductRequest.Update(
                    "런닝화", null, null, null
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999", HttpMethod.PATCH,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 상품입니다")
            );
        }

        @Test
        void 삭제된_상품이면_404_응답() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            fixture.deleteProduct(productId);

            ProductRequest.Update request = new ProductRequest.Update(
                    "런닝화", null, null, null
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + productId, HttpMethod.PATCH,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 요청_필드_규칙_위반_시_400_응답() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            ProductRequest.Update request = new ProductRequest.Update(
                    "", null, null, null
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + productId, HttpMethod.PATCH,
                    new HttpEntity<>(request, fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ProductRequest.Update request = new ProductRequest.Update(
                    "런닝화", null, null, null
            );

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.PATCH,
                    new HttpEntity<>(request, new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증 헤더가 필요합니다")
            );
        }

        @Test
        void 인증에_실패하면_401_응답() {
            ProductRequest.Update request = new ProductRequest.Update(
                    "런닝화", null, null, null
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.PATCH,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    @Nested
    class 상품_삭제 {

        @Test
        void 활성_상품을_삭제하면_200_응답() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            ResponseEntity<ApiResponse<Void>> response = deleteRequest(productId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 미존재_상품이면_404_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999", HttpMethod.DELETE,
                    new HttpEntity<>(fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 상품입니다")
            );
        }

        @Test
        void 이미_삭제된_상품을_다시_삭제해도_200_응답() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            fixture.deleteProduct(productId);

            ResponseEntity<ApiResponse<Void>> response = deleteRequest(productId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.DELETE,
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
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.DELETE,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    @Nested
    class 상품_상세_조회 {

        @Test
        void 활성_상품을_조회하면_200_응답과_상품_상세_정보를_반환한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = getDetail(productId);

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
                    () -> assertThat(response.getBody().data().status()).isEqualTo("ACTIVE"),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().updatedAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().deletedAt()).isNull()
            );
        }

        @Test
        void 삭제된_상품도_조회할_수_있으며_status가_DELETED로_표시된다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long productId = fixture.registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            fixture.deleteProduct(productId);

            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = getDetail(productId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(productId),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("DELETED"),
                    () -> assertThat(response.getBody().data().deletedAt()).isNotNull()
            );
        }

        @Test
        void 해당_ID의_상품_데이터가_존재하지_않으면_404_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999", HttpMethod.GET,
                    new HttpEntity<>(fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().message()).contains("존재하지 않는 상품입니다")
            );
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.GET,
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
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody().meta().message()).contains("인증에 실패했습니다")
            );
        }
    }

    @Nested
    class 상품_목록_조회 {

        @Test
        void 조건_없이_조회하면_전체_상품을_최신_등록순으로_페이징하여_200_응답한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            fixture.registerProduct(brandId, "운동화A", new BigDecimal("10000"), 10, "설명A");
            fixture.registerProduct(brandId, "운동화B", new BigDecimal("20000"), 20, "설명B");
            fixture.registerProduct(brandId, "운동화C", new BigDecimal("30000"), 30, "설명C");

            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response = getList("");

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
        void 삭제된_상품도_포함하여_반환한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            fixture.registerProduct(brandId, "운동화A", new BigDecimal("10000"), 10, "설명A");
            Long deletedProductId = fixture.registerProduct(brandId, "운동화B", new BigDecimal("20000"), 20, "설명B");
            fixture.deleteProduct(deletedProductId);

            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response = getList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(ProductAdminV1Dto.ProductResponse::status)
                            .containsExactly("DELETED", "ACTIVE")
            );
        }

        @Test
        void name_키워드로_검색하면_상품명에_해당_키워드가_포함된_상품만_반환한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            fixture.registerProduct(brandId, "런닝화", new BigDecimal("10000"), 10, "설명A");
            fixture.registerProduct(brandId, "운동화", new BigDecimal("20000"), 20, "설명B");
            fixture.registerProduct(brandId, "런닝 슈즈", new BigDecimal("30000"), 30, "설명C");

            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response =
                    getList("?name=런닝");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(ProductAdminV1Dto.ProductResponse::name)
                            .containsExactly("런닝 슈즈", "런닝화")
            );
        }

        @Test
        void brandId로_필터링하면_해당_브랜드에_속한_상품만_반환한다() {
            Long nikeId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long adidasId = fixture.registerBrand("아디다스", "독일 브랜드");
            fixture.registerProduct(nikeId, "나이키 운동화", new BigDecimal("10000"), 10, "설명");
            fixture.registerProduct(adidasId, "아디다스 운동화", new BigDecimal("20000"), 20, "설명");
            fixture.registerProduct(nikeId, "나이키 런닝화", new BigDecimal("30000"), 30, "설명");

            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response =
                    getList("?brandId=" + nikeId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(ProductAdminV1Dto.ProductResponse::brandId)
                            .containsOnly(nikeId)
            );
        }

        @Test
        void status_ACTIVE로_필터링하면_활성_상품만_반환한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            fixture.registerProduct(brandId, "운동화A", new BigDecimal("10000"), 10, "설명A");
            Long deletedProductId = fixture.registerProduct(brandId, "운동화B", new BigDecimal("20000"), 20, "설명B");
            fixture.deleteProduct(deletedProductId);

            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response =
                    getList("?status=ACTIVE");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("운동화A"),
                    () -> assertThat(response.getBody().data().content().get(0).status()).isEqualTo("ACTIVE")
            );
        }

        @Test
        void status_DELETED로_필터링하면_삭제된_상품만_반환한다() {
            Long brandId = fixture.registerBrand("나이키", "스포츠 브랜드");
            fixture.registerProduct(brandId, "운동화A", new BigDecimal("10000"), 10, "설명A");
            Long deletedProductId = fixture.registerProduct(brandId, "운동화B", new BigDecimal("20000"), 20, "설명B");
            fixture.deleteProduct(deletedProductId);

            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response =
                    getList("?status=DELETED");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("운동화B"),
                    () -> assertThat(response.getBody().data().content().get(0).status()).isEqualTo("DELETED")
            );
        }

        @Test
        void status에_유효하지_않은_값을_보내면_400_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?status=INVALID", HttpMethod.GET,
                    new HttpEntity<>(fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void name_검색과_brandId_필터와_status_필터를_동시에_적용할_수_있다() {
            Long nikeId = fixture.registerBrand("나이키", "스포츠 브랜드");
            Long adidasId = fixture.registerBrand("아디다스", "독일 브랜드");
            fixture.registerProduct(nikeId, "나이키 에어맥스", new BigDecimal("10000"), 10, "설명");
            Long deletedId = fixture.registerProduct(nikeId, "나이키 조던", new BigDecimal("20000"), 20, "설명");
            fixture.deleteProduct(deletedId);
            fixture.registerProduct(adidasId, "나이키 콜라보", new BigDecimal("30000"), 30, "설명");

            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response =
                    getList("?name=나이키&brandId=" + nikeId + "&status=ACTIVE");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("나이키 에어맥스")
            );
        }

        @Test
        void 결과가_없으면_빈_목록을_반환한다() {
            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response =
                    getList("?name=존재하지않는상품");

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
                    new HttpEntity<>(fixture.adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.GET,
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
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.GET,
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

    private ResponseEntity<ApiResponse<Void>> deleteRequest(Long productId) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + productId, HttpMethod.DELETE,
                new HttpEntity<>(fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> postRegister(
            ProductRequest.Register request) {
        return testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> patchUpdate(
            Long productId, ProductRequest.Update request) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + productId, HttpMethod.PATCH,
                new HttpEntity<>(request, fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> getDetail(Long productId) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + productId, HttpMethod.GET,
                new HttpEntity<>(fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> getList(String queryString) {
        return testRestTemplate.exchange(
                ENDPOINT + queryString, HttpMethod.GET,
                new HttpEntity<>(fixture.adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }
}

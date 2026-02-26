package com.loopers.interfaces.api.brand;

import com.loopers.application.brand.BrandRequest;
import com.loopers.application.product.ProductRequest;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.product.ProductAdminV1Dto;
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
class BrandAdminApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/brands";
    private static final String PRODUCT_ENDPOINT = "/api-admin/v1/products";
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
    class 브랜드_등록 {

        @Test
        void 유효한_정보로_등록하면_브랜드_정보가_반환된다() {
            BrandRequest.Register request = new BrandRequest.Register("나이키", "스포츠 브랜드");

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = postRegister(request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isNotNull(),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("스포츠 브랜드"),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("ACTIVE"),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().updatedAt()).isNotNull()
            );
        }

        @Test
        void 이미_존재하는_브랜드명이면_409_응답() {
            postRegister(new BrandRequest.Register("나이키", "스포츠 브랜드"));

            BrandRequest.Register duplicateRequest = new BrandRequest.Register("나이키", "다른 설명");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(duplicateRequest, adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 브랜드명이_빈값이면_400_응답() {
            BrandRequest.Register request = new BrandRequest.Register("", "설명");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증헤더가_누락되면_401_응답() {
            BrandRequest.Register request = new BrandRequest.Register("나이키", "스포츠 브랜드");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 인증에_실패하면_401_응답() {
            BrandRequest.Register request = new BrandRequest.Register("나이키", "스포츠 브랜드");

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 삭제된_브랜드와_동일한_이름으로_등록하면_409_응답() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            deleteBrand(brandId);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(new BrandRequest.Register("나이키", "다른 설명"), adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    class 브랜드_수정 {

        @Test
        void 유효한_정보로_수정하면_200_응답과_수정된_정보를_반환한다() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            BrandRequest.Update request = new BrandRequest.Update("아디다스", "독일 스포츠 브랜드");

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = patchUpdate(brandId, request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("아디다스"),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("독일 스포츠 브랜드")
            );
        }

        @Test
        void name만_보내면_name만_수정된다() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            BrandRequest.Update request = new BrandRequest.Update("아디다스", null);

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = patchUpdate(brandId, request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("아디다스"),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("스포츠 브랜드")
            );
        }

        @Test
        void description만_보내면_description만_수정된다() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            BrandRequest.Update request = new BrandRequest.Update(null, "변경된 설명");

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = patchUpdate(brandId, request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("변경된 설명")
            );
        }

        @Test
        void 중복_브랜드명이면_409_응답() {
            registerBrand("나이키", "스포츠 브랜드");
            Long adidasId = registerBrand("아디다스", "독일 스포츠 브랜드");
            BrandRequest.Update request = new BrandRequest.Update("나이키", null);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + adidasId, HttpMethod.PATCH,
                    new HttpEntity<>(request, adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 미존재_브랜드면_404_응답() {
            BrandRequest.Update request = new BrandRequest.Update("나이키", null);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999", HttpMethod.PATCH,
                    new HttpEntity<>(request, adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 입력_규칙_위반_시_400_응답() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            BrandRequest.Update request = new BrandRequest.Update("", null);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + brandId, HttpMethod.PATCH,
                    new HttpEntity<>(request, adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_누락이면_401_응답() {
            BrandRequest.Update request = new BrandRequest.Update("나이키", null);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.PATCH,
                    new HttpEntity<>(request, new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 인증_실패이면_401_응답() {
            BrandRequest.Update request = new BrandRequest.Update("나이키", null);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.PATCH,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 삭제된_브랜드와_동일한_이름으로_변경하면_409_응답() {
            Long nikeId = registerBrand("나이키", "스포츠 브랜드");
            deleteBrand(nikeId);
            Long adidasId = registerBrand("아디다스", "독일 스포츠 브랜드");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + adidasId, HttpMethod.PATCH,
                    new HttpEntity<>(new BrandRequest.Update("나이키", null), adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 자기_자신의_현재_이름과_동일한_이름으로_수정하면_200_응답() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            BrandRequest.Update request = new BrandRequest.Update("나이키", "변경된 설명");

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = patchUpdate(brandId, request);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("변경된 설명")
            );
        }

        @Test
        void 삭제된_브랜드를_수정하면_404_응답() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            deleteBrand(brandId);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + brandId, HttpMethod.PATCH,
                    new HttpEntity<>(new BrandRequest.Update("변경이름", null), adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class 브랜드_삭제 {

        @Test
        void 활성_브랜드를_삭제하면_200_응답() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");

            ResponseEntity<ApiResponse<Void>> response = deleteRequest(brandId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 이미_삭제된_브랜드를_다시_삭제해도_200_응답() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            deleteRequest(brandId);

            ResponseEntity<ApiResponse<Void>> response = deleteRequest(brandId);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 미존재_브랜드면_404_응답() {
            ResponseEntity<ApiResponse<Void>> response = deleteRequest(999L);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 인증헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.DELETE,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 브랜드_삭제_시_해당_브랜드의_활성_상품도_삭제_상태로_변경된다() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            registerProduct(brandId, "운동화", new BigDecimal("50000"), 100, "편한 운동화");
            registerProduct(brandId, "런닝화", new BigDecimal("60000"), 200, "가벼운 런닝화");

            deleteRequest(brandId);

            ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> response =
                    getProductList("?brandId=" + brandId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content())
                            .allSatisfy(product -> assertThat(product.status()).isEqualTo("DELETED"))
            );
        }
    }

    @Nested
    class 브랜드_목록_조회 {

        @Test
        void 조건_없이_조회하면_전체_브랜드를_최신_등록순으로_페이징하여_200_응답한다() {
            registerBrand("나이키", "스포츠 브랜드");
            registerBrand("아디다스", "독일 스포츠 브랜드");
            registerBrand("뉴발란스", "미국 스포츠 브랜드");

            ResponseEntity<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>> response = getList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(3),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("뉴발란스"),
                    () -> assertThat(response.getBody().data().content().get(1).name()).isEqualTo("아디다스"),
                    () -> assertThat(response.getBody().data().content().get(2).name()).isEqualTo("나이키"),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(3),
                    () -> assertThat(response.getBody().data().page()).isEqualTo(0),
                    () -> assertThat(response.getBody().data().size()).isEqualTo(20)
            );
        }

        @Test
        void 삭제된_브랜드도_포함하여_반환한다() {
            registerBrand("나이키", "스포츠 브랜드");
            Long deletedBrandId = registerBrand("아디다스", "독일 스포츠 브랜드");
            deleteBrand(deletedBrandId);

            ResponseEntity<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>> response = getList("");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(BrandAdminV1Dto.BrandResponse::status)
                            .containsExactly("DELETED", "ACTIVE")
            );
        }

        @Test
        void name_키워드로_검색하면_해당_키워드가_포함된_브랜드만_반환한다() {
            registerBrand("나이키", "스포츠 브랜드");
            registerBrand("아디다스", "독일 스포츠 브랜드");
            registerBrand("뉴발란스", "미국 스포츠 브랜드");

            ResponseEntity<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>> response =
                    getList("?name=나이키");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("나이키")
            );
        }

        @Test
        void status_ACTIVE로_필터링하면_활성_브랜드만_반환한다() {
            registerBrand("나이키", "스포츠 브랜드");
            Long deletedBrandId = registerBrand("아디다스", "독일 스포츠 브랜드");
            deleteBrand(deletedBrandId);

            ResponseEntity<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>> response =
                    getList("?status=ACTIVE");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("나이키"),
                    () -> assertThat(response.getBody().data().content().get(0).status()).isEqualTo("ACTIVE")
            );
        }

        @Test
        void status_DELETED로_필터링하면_삭제된_브랜드만_반환한다() {
            registerBrand("나이키", "스포츠 브랜드");
            Long deletedBrandId = registerBrand("아디다스", "독일 스포츠 브랜드");
            deleteBrand(deletedBrandId);

            ResponseEntity<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>> response =
                    getList("?status=DELETED");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("아디다스"),
                    () -> assertThat(response.getBody().data().content().get(0).status()).isEqualTo("DELETED")
            );
        }

        @Test
        void status에_유효하지_않은_값을_보내면_400_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?status=INVALID", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void name_검색과_status_필터를_동시에_적용할_수_있다() {
            registerBrand("나이키 에어", "에어 시리즈");
            Long deletedId = registerBrand("나이키 조던", "조던 시리즈");
            deleteBrand(deletedId);
            registerBrand("아디다스", "독일 스포츠 브랜드");

            ResponseEntity<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>> response =
                    getList("?name=나이키&status=ACTIVE");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("나이키 에어")
            );
        }

        @Test
        void 결과가_없으면_빈_목록을_반환한다() {
            ResponseEntity<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>> response =
                    getList("?name=존재하지않는브랜드");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0)
            );
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 요청_필드_규칙_위반_시_400_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "?page=-1", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class 브랜드_상세_조회 {

        @Test
        void 활성_브랜드를_조회하면_200_응답과_상세_정보를_반환한다() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = getDetail(brandId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                    () -> assertThat(response.getBody().data().description()).isEqualTo("스포츠 브랜드"),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("ACTIVE"),
                    () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().updatedAt()).isNotNull(),
                    () -> assertThat(response.getBody().data().deletedAt()).isNull()
            );
        }

        @Test
        void 삭제된_브랜드도_조회할_수_있으며_status가_DELETED로_표시된다() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            deleteBrand(brandId);

            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = getDetail(brandId);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("DELETED"),
                    () -> assertThat(response.getBody().data().deletedAt()).isNotNull()
            );
        }

        @Test
        void 미존재_브랜드면_404_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/999", HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 인증_헤더가_누락되면_401_응답() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // --- 헬퍼 메서드 ---

    private Long registerBrand(String name, String description) {
        BrandRequest.Register request = new BrandRequest.Register(name, description);
        ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = postRegister(request);
        return response.getBody().data().id();
    }

    private void deleteBrand(Long brandId) {
        deleteRequest(brandId);
    }

    private ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> postRegister(BrandRequest.Register request) {
        return testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> patchUpdate(Long brandId, BrandRequest.Update request) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + brandId, HttpMethod.PATCH,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<Void>> deleteRequest(Long brandId) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + brandId, HttpMethod.DELETE,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<PageResponse<BrandAdminV1Dto.BrandResponse>>> getList(String queryString) {
        return testRestTemplate.exchange(
                ENDPOINT + queryString, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> getDetail(Long brandId) {
        return testRestTemplate.exchange(
                ENDPOINT + "/" + brandId, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private Long registerProduct(Long brandId, String name, BigDecimal price, Integer stockQuantity, String description) {
        ProductRequest.Register request = new ProductRequest.Register(
                brandId, name, price, stockQuantity, description
        );
        ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductResponse>> response = testRestTemplate.exchange(
                PRODUCT_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private ResponseEntity<ApiResponse<PageResponse<ProductAdminV1Dto.ProductResponse>>> getProductList(String queryString) {
        return testRestTemplate.exchange(
                PRODUCT_ENDPOINT + queryString, HttpMethod.GET,
                new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", VALID_LDAP);
        return headers;
    }
}

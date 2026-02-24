package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BrandAdminApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/brands";
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
            BrandAdminV1Dto.RegisterRequest request = new BrandAdminV1Dto.RegisterRequest("나이키", "스포츠 브랜드");

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
            postRegister(new BrandAdminV1Dto.RegisterRequest("나이키", "스포츠 브랜드"));

            BrandAdminV1Dto.RegisterRequest duplicateRequest = new BrandAdminV1Dto.RegisterRequest("나이키", "다른 설명");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(duplicateRequest, adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 브랜드명이_빈값이면_400_응답() {
            BrandAdminV1Dto.RegisterRequest request = new BrandAdminV1Dto.RegisterRequest("", "설명");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증헤더가_누락되면_401_응답() {
            BrandAdminV1Dto.RegisterRequest request = new BrandAdminV1Dto.RegisterRequest("나이키", "스포츠 브랜드");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 인증에_실패하면_401_응답() {
            BrandAdminV1Dto.RegisterRequest request = new BrandAdminV1Dto.RegisterRequest("나이키", "스포츠 브랜드");

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    class 브랜드_수정 {

        @Test
        void 유효한_정보로_수정하면_200_응답과_수정된_정보를_반환한다() {
            Long brandId = registerBrand("나이키", "스포츠 브랜드");
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest("아디다스", "독일 스포츠 브랜드");

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
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest("아디다스", null);

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
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest(null, "변경된 설명");

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
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest("나이키", null);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + adidasId, HttpMethod.PATCH,
                    new HttpEntity<>(request, adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 미존재_브랜드면_404_응답() {
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest("나이키", null);

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
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest("", null);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/" + brandId, HttpMethod.PATCH,
                    new HttpEntity<>(request, adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_누락이면_401_응답() {
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest("나이키", null);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.PATCH,
                    new HttpEntity<>(request, new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void 인증_실패이면_401_응답() {
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest("나이키", null);

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong-ldap");

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/1", HttpMethod.PATCH,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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
    }

    // --- 헬퍼 메서드 ---

    private Long registerBrand(String name, String description) {
        BrandAdminV1Dto.RegisterRequest request = new BrandAdminV1Dto.RegisterRequest(name, description);
        ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = postRegister(request);
        return response.getBody().data().id();
    }

    private ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> postRegister(BrandAdminV1Dto.RegisterRequest request) {
        return testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> patchUpdate(Long brandId, BrandAdminV1Dto.UpdateRequest request) {
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

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", VALID_LDAP);
        return headers;
    }
}

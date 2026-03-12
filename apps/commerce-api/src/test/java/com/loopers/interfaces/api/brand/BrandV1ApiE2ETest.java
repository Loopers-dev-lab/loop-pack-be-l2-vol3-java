package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.brand.admin.BrandAdminV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String CUSTOMER_ENDPOINT = "/api/v1/brands";
    private static final String ADMIN_ENDPOINT = "/api-admin/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private Long createBrand(String name, String description) {
        BrandAdminV1Dto.CreateRequest request = new BrandAdminV1Dto.CreateRequest(name, description);
        ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            ADMIN_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    private void deleteBrand(Long brandId) {
        testRestTemplate.exchange(
            ADMIN_ENDPOINT + "/" + brandId, HttpMethod.DELETE, new HttpEntity<>(null, adminHeaders()),
            new ParameterizedTypeReference<ApiResponse<Object>>() {}
        );
    }

    // ========== Customer API ==========

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class CustomerGetBrand {

        @DisplayName("존재하는 브랜드를 조회하면 200과 브랜드 정보를 반환한다")
        @Test
        void returns200WithBrandInfo() {
            // given
            Long brandId = createBrand("나이키", "스포츠 브랜드");

            // when
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                CUSTOMER_ENDPOINT + "/" + brandId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(brandId),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("스포츠 브랜드")
            );
        }

        @DisplayName("미존재 브랜드를 조회하면 404를 반환한다")
        @Test
        void returns404WhenNotFound() {
            // given & when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                CUSTOMER_ENDPOINT + "/999", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드를 조회하면 404를 반환한다")
        @Test
        void returns404WhenDeleted() {
            // given
            Long brandId = createBrand("나이키", "스포츠 브랜드");
            deleteBrand(brandId);

            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                CUSTOMER_ENDPOINT + "/" + brandId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ========== Admin API ==========

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class AdminCreate {

        @DisplayName("유효한 정보로 등록하면 200과 브랜드 정보를 반환한다")
        @Test
        void returns200WithBrandInfo() {
            // given
            BrandAdminV1Dto.CreateRequest request = new BrandAdminV1Dto.CreateRequest("나이키", "스포츠 브랜드");

            // when
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("스포츠 브랜드"),
                () -> assertThat(response.getBody().data().id()).isNotNull()
            );
        }

        @DisplayName("중복 브랜드명이면 409를 반환한다")
        @Test
        void returns409OnDuplicateName() {
            // given
            createBrand("나이키", "스포츠 브랜드");
            BrandAdminV1Dto.CreateRequest request = new BrandAdminV1Dto.CreateRequest("나이키", "다른 설명");

            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("빈 이름이면 400을 반환한다")
        @Test
        void returns400OnEmptyName() {
            // given
            BrandAdminV1Dto.CreateRequest request = new BrandAdminV1Dto.CreateRequest("", "설명");

            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    class AdminGetAll {

        @DisplayName("브랜드 목록을 페이징하여 반환한다")
        @Test
        void returns200WithPagedList() {
            // given
            createBrand("나이키", "스포츠");
            createBrand("아디다스", "스포츠");

            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "?page=0&size=10", HttpMethod.GET, new HttpEntity<>(null, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    class AdminGetBrand {

        @DisplayName("존재하는 브랜드를 조회하면 200과 브랜드 정보를 반환한다")
        @Test
        void returns200WithBrandInfo() {
            // given
            Long brandId = createBrand("나이키", "스포츠 브랜드");

            // when
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "/" + brandId, HttpMethod.GET, new HttpEntity<>(null, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키")
            );
        }

        @DisplayName("미존재 브랜드를 조회하면 404를 반환한다")
        @Test
        void returns404WhenNotFound() {
            // given & when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "/999", HttpMethod.GET, new HttpEntity<>(null, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class AdminUpdate {

        @DisplayName("수정 성공 시 200과 수정된 브랜드 정보를 반환한다")
        @Test
        void returns200WithUpdatedInfo() {
            // given
            Long brandId = createBrand("나이키", "스포츠 브랜드");
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest("뉴발란스", "라이프스타일");

            // when
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "/" + brandId, HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("뉴발란스"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("라이프스타일")
            );
        }

        @DisplayName("중복명으로 변경하면 409를 반환한다")
        @Test
        void returns409OnDuplicateName() {
            // given
            createBrand("나이키", "스포츠");
            Long targetId = createBrand("아디다스", "스포츠");
            BrandAdminV1Dto.UpdateRequest request = new BrandAdminV1Dto.UpdateRequest("나이키", "변경 시도");

            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "/" + targetId, HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class AdminDelete {

        @DisplayName("삭제 성공 시 200을 반환한다")
        @Test
        void returns200OnSuccess() {
            // given
            Long brandId = createBrand("나이키", "스포츠 브랜드");

            // when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "/" + brandId, HttpMethod.DELETE, new HttpEntity<>(null, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("미존재 브랜드 삭제 시 404를 반환한다")
        @Test
        void returns404WhenNotFound() {
            // given & when
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ADMIN_ENDPOINT + "/999", HttpMethod.DELETE, new HttpEntity<>(null, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}

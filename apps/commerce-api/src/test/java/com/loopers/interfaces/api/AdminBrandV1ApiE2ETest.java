package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.AdminBrandV1Dto;
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
class AdminBrandV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/brands";
    private static final String HEADER_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP = "loopers.admin";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public AdminBrandV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LDAP, ADMIN_LDAP);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private AdminBrandV1Dto.BrandResponse createBrand(String name) {
        AdminBrandV1Dto.CreateRequest request = new AdminBrandV1Dto.CreateRequest(name);
        ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data();
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class Create {

        @DisplayName("올바른 요청이면, 200 OK와 함께 브랜드 정보를 반환한다.")
        @Test
        void returnsBrandInfo_whenValidRequest() {
            // arrange
            AdminBrandV1Dto.CreateRequest request = new AdminBrandV1Dto.CreateRequest("나이키");

            // act
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().id()).isNotNull()
            );
        }

        @DisplayName("이름이 비어있으면, 400 BAD_REQUEST를 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            AdminBrandV1Dto.CreateRequest request = new AdminBrandV1Dto.CreateRequest("");

            // act
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("어드민 인증이 없으면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenNoAdminAuth() {
            // arrange
            AdminBrandV1Dto.CreateRequest request = new AdminBrandV1Dto.CreateRequest("나이키");
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            // act
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    class GetAll {

        @DisplayName("브랜드가 존재하면, 페이지 결과를 반환한다.")
        @Test
        void returnsPageResult_whenBrandsExist() {
            // arrange
            createBrand("나이키");
            createBrand("아디다스");
            createBrand("뉴발란스");

            // act
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandPageResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "?page=0&size=2", HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().content()).hasSize(2),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(3),
                () -> assertThat(response.getBody().data().totalPages()).isEqualTo(2)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    class GetById {

        @DisplayName("존재하는 브랜드이면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrandInfo_whenBrandExists() {
            // arrange
            AdminBrandV1Dto.BrandResponse created = createBrand("나이키");

            // act
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + created.id(), HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().createdAt()).isNotNull()
            );
        }

        @DisplayName("존재하지 않는 브랜드이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // act
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/999", HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class Update {

        @DisplayName("올바른 요청이면, 수정된 브랜드 정보를 반환한다.")
        @Test
        void returnsUpdatedBrand_whenValidRequest() {
            // arrange
            AdminBrandV1Dto.BrandResponse created = createBrand("나이키");
            AdminBrandV1Dto.UpdateRequest request = new AdminBrandV1Dto.UpdateRequest("아디다스");

            // act
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + created.id(), HttpMethod.PUT, new HttpEntity<>(request, adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("아디다스")
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class Delete {

        @DisplayName("존재하는 브랜드이면, 200 OK를 반환하고 조회되지 않는다.")
        @Test
        void deletesAndReturnsSuccess_whenBrandExists() {
            // arrange
            AdminBrandV1Dto.BrandResponse created = createBrand("나이키");

            // act
            ResponseEntity<ApiResponse<Void>> deleteResponse = testRestTemplate.exchange(
                ENDPOINT + "/" + created.id(), HttpMethod.DELETE, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // assert - delete succeeds
            assertTrue(deleteResponse.getStatusCode().is2xxSuccessful());

            // assert - brand is no longer retrievable
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> getResponse = testRestTemplate.exchange(
                ENDPOINT + "/" + created.id(), HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 브랜드이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT + "/999", HttpMethod.DELETE, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}

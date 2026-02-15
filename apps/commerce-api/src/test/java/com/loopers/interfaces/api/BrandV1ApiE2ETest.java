package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.AdminBrandV1Dto;
import com.loopers.interfaces.api.brand.BrandV1Dto;
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

    private static final String ENDPOINT = "/api/v1/brands";
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
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private Long createBrandViaAdmin(String name) {
        AdminBrandV1Dto.CreateRequest request = new AdminBrandV1Dto.CreateRequest(name);
        ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
            ADMIN_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().id();
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetById {

        @DisplayName("존재하는 브랜드이면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrandInfo_whenBrandExists() {
            // arrange
            Long brandId = createBrandViaAdmin("나이키");

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + brandId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(brandId),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키")
            );
        }

        @DisplayName("존재하지 않는 브랜드이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenBrandDoesNotExist() {
            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/999", HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("인증 없이도 조회할 수 있다.")
        @Test
        void returnsBrandInfo_withoutAuth() {
            // arrange
            Long brandId = createBrandViaAdmin("아디다스");

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + brandId, HttpMethod.GET, null,
                new ParameterizedTypeReference<>() {}
            );

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }
    }
}

package com.loopers.interfaces.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.brand.dto.AdminBrandV1Dto;
import com.loopers.interfaces.brand.dto.BrandV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Brand V1 API 시나리오 테스트")
class BrandV1ApiScenarioTest {

    private static final String ENDPOINT_BRANDS = "/api/v1/brands";
    private static final String ADMIN_ENDPOINT_BRANDS = "/api-admin/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandV1ApiScenarioTest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
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

    @DisplayName("브랜드 전체 플로우: 등록(Admin) -> 조회(Public) -> 수정(Admin) -> 조회(Public) -> 삭제(Admin) -> 조회(Public, 404)")
    @Test
    void fullBrandLifecycleScenario() {
        // ===== 1단계: 브랜드 등록 (Admin API) =====
        AdminBrandV1Dto.RegisterRequest registerRequest = new AdminBrandV1Dto.RegisterRequest("나이키");

        ParameterizedTypeReference<ApiResponse<Object>> objectResponseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<Object>> registerResponse =
            testRestTemplate.exchange(ADMIN_ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(registerRequest, adminHeaders()), objectResponseType);

        assertAll(
            "브랜드 등록 성공 검증",
            () -> assertTrue(registerResponse.getStatusCode().is2xxSuccessful()),
            () -> assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.OK)
        );

        Long brandId = brandJpaRepository.findByNameAndDeletedAtIsNull("나이키").get().getId();

        // ===== 2단계: 브랜드 조회 (Public API) =====
        ParameterizedTypeReference<ApiResponse<BrandV1Dto.DetailResponse>> detailResponseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<BrandV1Dto.DetailResponse>> getResponse =
            testRestTemplate.exchange(ENDPOINT_BRANDS + "/" + brandId, HttpMethod.GET, null, detailResponseType);

        assertAll(
            "브랜드 조회 성공 검증",
            () -> assertTrue(getResponse.getStatusCode().is2xxSuccessful()),
            () -> assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(getResponse.getBody()).isNotNull(),
            () -> assertThat(getResponse.getBody().data().id()).isEqualTo(brandId),
            () -> assertThat(getResponse.getBody().data().name()).isEqualTo("나이키")
        );

        // ===== 3단계: 브랜드 수정 (Admin API) =====
        AdminBrandV1Dto.UpdateRequest updateRequest = new AdminBrandV1Dto.UpdateRequest("아디다스");

        ResponseEntity<ApiResponse<Object>> updateResponse =
            testRestTemplate.exchange(ADMIN_ENDPOINT_BRANDS + "/" + brandId, HttpMethod.PUT, new HttpEntity<>(updateRequest, adminHeaders()), objectResponseType);

        assertAll(
            "브랜드 수정 성공 검증",
            () -> assertTrue(updateResponse.getStatusCode().is2xxSuccessful()),
            () -> assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK)
        );

        // ===== 4단계: 수정 후 조회 (Public API) =====
        ResponseEntity<ApiResponse<BrandV1Dto.DetailResponse>> getAfterUpdateResponse =
            testRestTemplate.exchange(ENDPOINT_BRANDS + "/" + brandId, HttpMethod.GET, null, detailResponseType);

        assertAll(
            "수정 후 조회 검증",
            () -> assertTrue(getAfterUpdateResponse.getStatusCode().is2xxSuccessful()),
            () -> assertThat(getAfterUpdateResponse.getBody()).isNotNull(),
            () -> assertThat(getAfterUpdateResponse.getBody().data().name()).isEqualTo("아디다스")
        );

        // ===== 5단계: 브랜드 삭제 (Admin API) =====
        ResponseEntity<ApiResponse<Object>> deleteResponse =
            testRestTemplate.exchange(ADMIN_ENDPOINT_BRANDS + "/" + brandId, HttpMethod.DELETE, new HttpEntity<>(null, adminHeaders()), objectResponseType);

        assertAll(
            "브랜드 삭제 성공 검증",
            () -> assertTrue(deleteResponse.getStatusCode().is2xxSuccessful()),
            () -> assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK)
        );

        // ===== 6단계: 삭제 후 조회 (Public API, 404) =====
        ResponseEntity<ApiResponse<Object>> getAfterDeleteResponse =
            testRestTemplate.exchange(ENDPOINT_BRANDS + "/" + brandId, HttpMethod.GET, null, objectResponseType);

        assertAll(
            "삭제 후 조회 실패 검증",
            () -> assertTrue(getAfterDeleteResponse.getStatusCode().is4xxClientError()),
            () -> assertThat(getAfterDeleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
        );
    }
}

package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.interfaces.api.brand.BrandAdminV1Dto;
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

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandAdminV1ApiE2ETest {

    private static final String ENDPOINT_BASE = "/api-admin/v1/brands";
    private static final Function<Long, String> ENDPOINT_BY_ID = id -> ENDPOINT_BASE + "/" + id;

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandAdminV1ApiE2ETest(
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
        headers.set("X-Loopers-AdminLdap", "admin");
        return headers;
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class Create {

        @DisplayName("유효한 요청이면 브랜드를 등록하고 200을 반환한다")
        @Test
        void createsSuccessfully() {
            // arrange
            BrandAdminV1Dto.CreateRequest request = new BrandAdminV1Dto.CreateRequest("나이키", "스포츠 브랜드");
            HttpEntity<BrandAdminV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());
            // act
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_BASE, HttpMethod.POST, entity,
                new ParameterizedTypeReference<>() {}
            );
            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키")
            );
        }

        @DisplayName("중복 이름이면 409를 반환한다")
        @Test
        void returnsConflictOnDuplicateName() {
            // arrange
            brandJpaRepository.save(new BrandModel("나이키", "기존"));
            BrandAdminV1Dto.CreateRequest request = new BrandAdminV1Dto.CreateRequest("나이키", "신규");
            HttpEntity<BrandAdminV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());
            // act
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_BASE, HttpMethod.POST, entity,
                new ParameterizedTypeReference<>() {}
            );
            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @DisplayName("관리자 헤더가 없으면 401을 반환한다")
        @Test
        void returnsUnauthorizedWithoutAdminHeader() {
            // arrange
            BrandAdminV1Dto.CreateRequest request = new BrandAdminV1Dto.CreateRequest("나이키", "스포츠");
            HttpEntity<BrandAdminV1Dto.CreateRequest> entity = new HttpEntity<>(request);
            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_BASE, HttpMethod.POST, entity,
                new ParameterizedTypeReference<>() {}
            );
            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{id}")
    @Nested
    class GetById {

        @DisplayName("존재하는 ID면 브랜드 정보를 반환한다")
        @Test
        void returnsForExistingId() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠"));
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());
            // act
            ResponseEntity<ApiResponse<BrandAdminV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_BY_ID.apply(brand.getId()), HttpMethod.GET, entity,
                new ParameterizedTypeReference<>() {}
            );
            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키")
            );
        }

        @DisplayName("존재하지 않는 ID면 404를 반환한다")
        @Test
        void returnsNotFoundForInvalidId() {
            // arrange
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());
            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_BY_ID.apply(999L), HttpMethod.GET, entity,
                new ParameterizedTypeReference<>() {}
            );
            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{id}")
    @Nested
    class Delete {

        @DisplayName("존재하는 브랜드를 삭제하면 200을 반환한다")
        @Test
        void deletesSuccessfully() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠"));
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());
            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_BY_ID.apply(brand.getId()), HttpMethod.DELETE, entity,
                new ParameterizedTypeReference<>() {}
            );
            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }
    }
}

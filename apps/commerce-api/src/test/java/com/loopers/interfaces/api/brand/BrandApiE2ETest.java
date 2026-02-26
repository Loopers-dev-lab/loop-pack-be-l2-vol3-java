package com.loopers.interfaces.api.brand;

import com.loopers.interfaces.api.brand.dto.BrandCreateRequest;
import com.loopers.interfaces.api.brand.dto.BrandResponse;
import com.loopers.interfaces.api.brand.dto.BrandUpdateRequest;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class BrandApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final String ADMIN_BASE_URL = "/api-admin/v1/brands";
    private static final String PUBLIC_BASE_URL = "/api/v1/brands";

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("E2E: 브랜드 CRUD 시나리오")
    class BrandCrudE2E {

        @Test
        @DisplayName("브랜드 생성 → 조회 성공")
        void create_then_get() {
            // given
            var request = new BrandCreateRequest("나이키", "스포츠 브랜드");

            // when - 생성
            ResponseEntity<Void> createResponse = restTemplate.exchange(
                    ADMIN_BASE_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(request, createAdminHeaders()),
                    Void.class
            );

            // then
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // when - 조회
            ResponseEntity<BrandResponse> getResponse = restTemplate.getForEntity(
                    PUBLIC_BASE_URL + "/1",
                    BrandResponse.class
            );

            // then
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody()).isNotNull();
            assertThat(getResponse.getBody().name()).isEqualTo("나이키");
        }

        @Test
        @DisplayName("브랜드 생성 → 수정 → 조회 확인")
        void create_update_then_get() {
            // given
            createBrand("나이키", "원래 설명");

            // when - 수정
            var updateRequest = new BrandUpdateRequest("아디다스", "변경된 설명");
            ResponseEntity<Void> updateResponse = restTemplate.exchange(
                    ADMIN_BASE_URL + "/1",
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest, createAdminHeaders()),
                    Void.class
            );
            assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // then - 조회
            ResponseEntity<BrandResponse> getResponse = restTemplate.getForEntity(
                    PUBLIC_BASE_URL + "/1",
                    BrandResponse.class
            );
            assertThat(getResponse.getBody().name()).isEqualTo("아디다스");
            assertThat(getResponse.getBody().description()).isEqualTo("변경된 설명");
        }

        @Test
        @DisplayName("브랜드 생성 → 삭제 → 조회 실패")
        void create_delete_then_getFail() {
            // given
            createBrand("나이키", "스포츠 브랜드");

            // when - 삭제
            ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                    ADMIN_BASE_URL + "/1",
                    HttpMethod.DELETE,
                    new HttpEntity<>(createAdminHeaders()),
                    Void.class
            );
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // then - 삭제된 브랜드 조회 시 실패
            ResponseEntity<String> getResponse = restTemplate.getForEntity(
                    PUBLIC_BASE_URL + "/1",
                    String.class
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("E2E: 관리자 인증 시나리오")
    class AdminAuthE2E {

        @Test
        @DisplayName("관리자 인증 없이 브랜드 생성 실패")
        void createBrand_unauthorized() {
            var request = new BrandCreateRequest("나이키", "스포츠 브랜드");

            ResponseEntity<String> response = restTemplate.postForEntity(
                    ADMIN_BASE_URL,
                    request,
                    String.class
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    private HttpHeaders createAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void createBrand(String name, String description) {
        var request = new BrandCreateRequest(name, description);
        ResponseEntity<Void> response = restTemplate.exchange(
                ADMIN_BASE_URL,
                HttpMethod.POST,
                new HttpEntity<>(request, createAdminHeaders()),
                Void.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

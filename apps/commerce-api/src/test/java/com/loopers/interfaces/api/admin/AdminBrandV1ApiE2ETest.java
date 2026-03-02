package com.loopers.interfaces.api.admin;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AdminAuthInterceptor;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * 어드민 브랜드 API E2E. 모든 요청은 유효한 어드민 인증(LDAP+서명)이 필요하다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class AdminBrandV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/brands";
    private static final String LDAP_ID = "admin-brand-e2e";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    /** 서버와 동일한 시크릿으로 서명해 유효한 어드민 헤더를 만들기 위함. 없으면 서명 불일치로 401. */
    @Value("${loopers.admin.auth.signing-secret:change-me-in-production}")
    private String signingSecret;

    private Long createdBrandId;

    @BeforeEach
    void setUp() {
        AdminBrandV1Dto.CreateBrandRequest body = new AdminBrandV1Dto.CreateBrandRequest("E2E어드민브랜드");
        ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> res = testRestTemplate.exchange(
            ENDPOINT, HttpMethod.POST, new HttpEntity<>(body, adminHeaders()),
            new ParameterizedTypeReference<>() {});
        ApiResponse<AdminBrandV1Dto.BrandResponse> resBody = res.getBody();
        assertThat(resBody).isNotNull();
        createdBrandId = resBody.data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        String signature = AdminAuthInterceptor.sign(LDAP_ID, signingSecret);
        HttpHeaders h = new HttpHeaders();
        h.set(AdminAuthInterceptor.HEADER_LDAP, LDAP_ID);
        h.set(AdminAuthInterceptor.HEADER_ADMIN_SIGNATURE, signature);
        return h;
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class CreateBrand {

        @Test
        @DisplayName("유효한 어드민 인증으로 브랜드 생성 시 201 및 생성된 정보 반환")
        void withValidAuth_shouldReturn201() {
            AdminBrandV1Dto.CreateBrandRequest body = new AdminBrandV1Dto.CreateBrandRequest("새브랜드");

            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getBody()).isNotNull();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                () -> assertThat(response.getBody().data().name()).isEqualTo("새브랜드"),
                () -> assertThat(response.getBody().data().id()).isNotNull()
            );
        }

        @Test
        @DisplayName("어드민 인증 없이 요청 시 401 반환")
        void withoutAuth_shouldReturn401() {
            AdminBrandV1Dto.CreateBrandRequest body = new AdminBrandV1Dto.CreateBrandRequest("무단브랜드");

            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST, new HttpEntity<>(body), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    class GetBrand {

        @Test
        @DisplayName("유효한 어드민 인증으로 조회 시 200 및 브랜드 정보 반환")
        void withValidAuth_shouldReturn200() {
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + createdBrandId, HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getBody()).isNotNull();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().id()).isEqualTo(createdBrandId),
                () -> assertThat(response.getBody().data().name()).isEqualTo("E2E어드민브랜드")
            );
        }

        @Test
        @DisplayName("존재하지 않는 brandId 조회 시 404 반환")
        void whenNotFound_shouldReturn404() {
            long nonExistent = 999_999L;

            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + nonExistent, HttpMethod.GET, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        @DisplayName("어드민 인증 없이 조회 시 401 반환")
        void withoutAuth_shouldReturn401() {
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + createdBrandId, HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class UpdateBrand {

        @Test
        @DisplayName("유효한 어드민 인증으로 수정 시 200 및 변경된 이름 반환")
        void withValidAuth_shouldReturn200() {
            AdminBrandV1Dto.UpdateBrandRequest body = new AdminBrandV1Dto.UpdateBrandRequest("수정된브랜드명");

            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + createdBrandId, HttpMethod.PUT, new HttpEntity<>(body, adminHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getBody()).isNotNull();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("수정된브랜드명")
            );
        }

        @Test
        @DisplayName("어드민 인증 없이 수정 시 401 반환")
        void withoutAuth_shouldReturn401() {
            AdminBrandV1Dto.UpdateBrandRequest body = new AdminBrandV1Dto.UpdateBrandRequest("무단수정");

            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + createdBrandId, HttpMethod.PUT, new HttpEntity<>(body),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class DeleteBrand {

        @Test
        @DisplayName("유효한 어드민 인증으로 삭제 시 204 반환")
        void withValidAuth_shouldReturn204() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + createdBrandId, HttpMethod.DELETE, new HttpEntity<>(adminHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }

        @Test
        @DisplayName("어드민 인증 없이 삭제 시 401 반환")
        void withoutAuth_shouldReturn401() {
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT + "/" + createdBrandId, HttpMethod.DELETE, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}

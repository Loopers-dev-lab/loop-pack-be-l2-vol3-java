package com.loopers.interfaces.api.admin;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AdminAuthInterceptor;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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
 * 어드민 API 인증 E2E.
 * - 헤더 없음 / LDAP 단일 헤더만 / 잘못된 서명 → 401
 * - 유효한 관리자 서명(헤더 커넥터 시나리오) → 통과
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class AdminAuthE2ETest {

    private static final String ENDPOINT_ADMIN_BRANDS = "/api-admin/v1/brands";
    private static final String LDAP_ID = "admin-ldap-user";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    /** 인터셉터와 동일한 시크릿 사용. 테스트용으로 {@code @TestPropertySource} 또는 기본값. */
    @Value("${loopers.admin.auth.signing-secret:change-me-in-production}")
    private String signingSecret;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    /** 유효한 관리자 헤더: LDAP + 서명. 게이트웨이가 보내는 형태를 시뮬레이션. */
    private HttpHeaders validAdminHeaders() {
        String signature = AdminAuthInterceptor.sign(LDAP_ID, signingSecret);
        HttpHeaders h = new HttpHeaders();
        h.set(AdminAuthInterceptor.HEADER_LDAP, LDAP_ID);
        h.set(AdminAuthInterceptor.HEADER_ADMIN_SIGNATURE, signature);
        return h;
    }

    @DisplayName("어드민 API 인증 실패 케이스")
    @Nested
    class Unauthorized {

        @Test
        @DisplayName("헤더 없이 요청 시 401 반환 — 인증 필수임을 검증")
        void request_withNoHeaders_shouldReturn401() {
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN_BRANDS + "/1", HttpMethod.GET, new HttpEntity<>(null),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("LDAP 헤더만 있고 서명 없을 때 401 반환 — 서명 필수·단일 헤더 우회 불가 검증")
        void request_withOnlyLdapHeaderNoSignature_shouldReturn401() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(AdminAuthInterceptor.HEADER_LDAP, LDAP_ID);

            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN_BRANDS + "/1", HttpMethod.GET, new HttpEntity<>(null, headers),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("잘못된 서명으로 요청 시 401 반환 — 서명 검증(위변조 차단) 동작 확인")
        void request_withWrongSignature_shouldReturn401() {
            HttpHeaders headers = new HttpHeaders();
            headers.set(AdminAuthInterceptor.HEADER_LDAP, LDAP_ID);
            headers.set(AdminAuthInterceptor.HEADER_ADMIN_SIGNATURE, "invalid-signature-hex");

            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_ADMIN_BRANDS + "/1", HttpMethod.GET, new HttpEntity<>(null, headers),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("어드민 API 인증 성공 케이스 (헤더 커넥터·유효 자격 증명)")
    @Nested
    class Authorized {

        @Test
        @DisplayName("유효한 LDAP+서명으로 요청 시 통과 — 브랜드 생성·조회 성공으로 관리자 자격 증명 검증")
        void request_withValidAdminCredentials_shouldPassThrough() {
            AdminBrandV1Dto.CreateBrandRequest body = new AdminBrandV1Dto.CreateBrandRequest("E2E어드민브랜드");
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> createResponse = testRestTemplate.exchange(
                ENDPOINT_ADMIN_BRANDS, HttpMethod.POST, new HttpEntity<>(body, validAdminHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(createResponse.getBody()).isNotNull();
            assertAll(
                () -> assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(createResponse.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                () -> assertThat(createResponse.getBody().data().name()).isEqualTo("E2E어드민브랜드")
            );

            Long brandId = createResponse.getBody().data().id();
            ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> getResponse = testRestTemplate.exchange(
                ENDPOINT_ADMIN_BRANDS + "/" + brandId, HttpMethod.GET, new HttpEntity<>(validAdminHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(getResponse.getBody()).isNotNull();
            assertAll(
                () -> assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(getResponse.getBody().data().id()).isEqualTo(brandId)
            );
        }
    }
}

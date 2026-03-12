package com.loopers.interfaces.brand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.brand.dto.AdminBrandV1Dto;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminBrandV1ApiE2ETest {

    private static final String ENDPOINT_BRANDS = "/api-admin/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public AdminBrandV1ApiE2ETest(
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

    @DisplayName("LDAP 인증")
    @Nested
    class Authentication {

        @DisplayName("LDAP 헤더 없이 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoLdapHeader() {
            // arrange
            AdminBrandV1Dto.RegisterRequest request = new AdminBrandV1Dto.RegisterRequest("나이키");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(request), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(brandJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("잘못된 LDAP 헤더 값으로 요청하면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenInvalidLdapHeader() {
            // arrange
            AdminBrandV1Dto.RegisterRequest request = new AdminBrandV1Dto.RegisterRequest("나이키");
            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong.value");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                () -> assertThat(brandJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("GET 요청도 LDAP 헤더 없이는 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenGetWithoutLdapHeader() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.GET, null, responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class Register {

        @DisplayName("유효한 브랜드명을 주면, 브랜드 등록에 성공한다.")
        @Test
        void returnsSuccess_whenValidBrandNameIsProvided() {
            // arrange
            AdminBrandV1Dto.RegisterRequest request = new AdminBrandV1Dto.RegisterRequest("나이키");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(brandJpaRepository.count()).isEqualTo(1)
            );
        }

        @DisplayName("브랜드명이 빈값이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenBrandNameIsBlank() {
            // arrange
            AdminBrandV1Dto.RegisterRequest request = new AdminBrandV1Dto.RegisterRequest("");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(brandJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("브랜드명이 99자를 초과하면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenBrandNameIsTooLong() {
            // arrange
            AdminBrandV1Dto.RegisterRequest request = new AdminBrandV1Dto.RegisterRequest("a".repeat(100));

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(request, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                () -> assertThat(brandJpaRepository.count()).isEqualTo(0)
            );
        }

        @DisplayName("이미 존재하는 브랜드명이면, 409 CONFLICT 응답을 받는다.")
        @Test
        void throwsConflict_whenBrandNameAlreadyExists() {
            // arrange
            AdminBrandV1Dto.RegisterRequest firstRequest = new AdminBrandV1Dto.RegisterRequest("나이키");
            AdminBrandV1Dto.RegisterRequest secondRequest = new AdminBrandV1Dto.RegisterRequest("나이키");

            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(firstRequest, adminHeaders()), responseType);

            // act
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(secondRequest, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(brandJpaRepository.count()).isEqualTo(1)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/brands")
    @Nested
    class List {

        @DisplayName("브랜드 목록을 조회하면, 페이지네이션된 목록을 반환한다.")
        @Test
        void returnsPaginatedList_whenBrandsExist() {
            // arrange
            ParameterizedTypeReference<ApiResponse<Object>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(new AdminBrandV1Dto.RegisterRequest("나이키"), adminHeaders()), registerResponseType);
            testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(new AdminBrandV1Dto.RegisterRequest("아디다스"), adminHeaders()), registerResponseType);
            testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(new AdminBrandV1Dto.RegisterRequest("푸마"), adminHeaders()), registerResponseType);

            // act
            ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.ListResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminBrandV1Dto.ListResponse>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS + "?page=0&size=2", HttpMethod.GET, new HttpEntity<>(null, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(3),
                () -> assertThat(response.getBody().data().totalPages()).isEqualTo(2),
                () -> assertThat(response.getBody().data().items()).hasSize(2)
            );
        }

        @DisplayName("브랜드가 없으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenNoBrandsExist() {
            // act
            ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.ListResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminBrandV1Dto.ListResponse>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS + "?page=0&size=20", HttpMethod.GET, new HttpEntity<>(null, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().totalElements()).isEqualTo(0),
                () -> assertThat(response.getBody().data().items()).isEmpty()
            );
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId}")
    @Nested
    class GetById {

        @DisplayName("존재하는 브랜드를 조회하면, 상세 정보를 반환한다.")
        @Test
        void returnsBrandDetail_whenBrandExists() {
            // arrange
            ParameterizedTypeReference<ApiResponse<Object>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(new AdminBrandV1Dto.RegisterRequest("나이키"), adminHeaders()), registerResponseType);

            Long brandId = brandJpaRepository.findByNameAndDeletedAtIsNull("나이키").get().getId();

            // act
            ParameterizedTypeReference<ApiResponse<AdminBrandV1Dto.DetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<AdminBrandV1Dto.DetailResponse>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS + "/" + brandId, HttpMethod.GET, new HttpEntity<>(null, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(brandId),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().createdAt()).isNotNull(),
                () -> assertThat(response.getBody().data().updatedAt()).isNotNull(),
                () -> assertThat(response.getBody().data().deletedAt()).isNull()
            );
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS + "/999", HttpMethod.GET, new HttpEntity<>(null, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId}")
    @Nested
    class Update {

        @DisplayName("유효한 수정 요청이면, 브랜드가 수정된다.")
        @Test
        void returnsSuccess_whenValidUpdateRequest() {
            // arrange
            ParameterizedTypeReference<ApiResponse<Object>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(new AdminBrandV1Dto.RegisterRequest("나이키"), adminHeaders()), registerResponseType);

            Long brandId = brandJpaRepository.findByNameAndDeletedAtIsNull("나이키").get().getId();
            AdminBrandV1Dto.UpdateRequest updateRequest = new AdminBrandV1Dto.UpdateRequest("아디다스");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS + "/" + brandId, HttpMethod.PUT, new HttpEntity<>(updateRequest, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(brandJpaRepository.findByNameAndDeletedAtIsNull("아디다스")).isPresent()
            );
        }

        @DisplayName("존재하지 않는 브랜드를 수정하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // arrange
            AdminBrandV1Dto.UpdateRequest updateRequest = new AdminBrandV1Dto.UpdateRequest("아디다스");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS + "/999", HttpMethod.PUT, new HttpEntity<>(updateRequest, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @DisplayName("이미 존재하는 브랜드명으로 수정하면, 409 CONFLICT 응답을 받는다.")
        @Test
        void throwsConflict_whenBrandNameAlreadyExists() {
            // arrange
            ParameterizedTypeReference<ApiResponse<Object>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(new AdminBrandV1Dto.RegisterRequest("나이키"), adminHeaders()), registerResponseType);
            testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(new AdminBrandV1Dto.RegisterRequest("아디다스"), adminHeaders()), registerResponseType);

            Long nikeId = brandJpaRepository.findByNameAndDeletedAtIsNull("나이키").get().getId();
            AdminBrandV1Dto.UpdateRequest updateRequest = new AdminBrandV1Dto.UpdateRequest("아디다스");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS + "/" + nikeId, HttpMethod.PUT, new HttpEntity<>(updateRequest, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
            );
        }

        @DisplayName("브랜드명이 빈값이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void throwsBadRequest_whenBrandNameIsBlank() {
            // arrange
            ParameterizedTypeReference<ApiResponse<Object>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(new AdminBrandV1Dto.RegisterRequest("나이키"), adminHeaders()), registerResponseType);

            Long brandId = brandJpaRepository.findByNameAndDeletedAtIsNull("나이키").get().getId();
            AdminBrandV1Dto.UpdateRequest updateRequest = new AdminBrandV1Dto.UpdateRequest("");

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS + "/" + brandId, HttpMethod.PUT, new HttpEntity<>(updateRequest, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId}")
    @Nested
    class Delete {

        @DisplayName("존재하는 브랜드를 삭제하면, 성공한다.")
        @Test
        void returnsSuccess_whenBrandExists() {
            // arrange
            ParameterizedTypeReference<ApiResponse<Object>> registerResponseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.POST, new HttpEntity<>(new AdminBrandV1Dto.RegisterRequest("나이키"), adminHeaders()), registerResponseType);

            Long brandId = brandJpaRepository.findByNameAndDeletedAtIsNull("나이키").get().getId();

            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS + "/" + brandId, HttpMethod.DELETE, new HttpEntity<>(null, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(brandJpaRepository.findByNameAndDeletedAtIsNull("나이키")).isEmpty()
            );
        }

        @DisplayName("존재하지 않는 브랜드를 삭제하면, 404 NOT_FOUND 응답을 받는다.")
        @Test
        void throwsNotFound_whenBrandDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Object>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Object>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS + "/999", HttpMethod.DELETE, new HttpEntity<>(null, adminHeaders()), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }
    }
}

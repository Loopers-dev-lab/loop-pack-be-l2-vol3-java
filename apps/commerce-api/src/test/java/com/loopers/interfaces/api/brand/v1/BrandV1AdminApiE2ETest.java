package com.loopers.interfaces.api.brand.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

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

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1AdminApiE2ETest {

    private static final String BRAND_ADMIN_ENDPOINT = "/api-admin/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandV1AdminApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api-admin/v1/brands")
    @Nested
    class CreateBrand {

        @DisplayName("유효한 정보를 입력하면, 브랜드 생성에 성공한다.")
        @Test
        void createsBrand_whenValidInputProvided() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "브랜드 설명");

            // act
            var response = createBrandRequest(request, adminHeaders());

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().brandId()).isNotNull()
            );
        }

        @DisplayName("X-Loopers-Ldap 헤더가 없으면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenNoLdapHeader() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "브랜드 설명");

            // act
            var response = createBrandRequest(request, new HttpHeaders());

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.UNAUTHORIZED.getCode())
            );
        }

        @DisplayName("X-Loopers-Ldap 헤더 값이 잘못되면, 401 UNAUTHORIZED 응답을 받는다.")
        @Test
        void returnsUnauthorized_whenLdapHeaderValueIsWrong() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "브랜드 설명");
            var headers = new HttpHeaders();
            headers.set("X-Loopers-Ldap", "wrong.value");

            // act
            var response = createBrandRequest(request, headers);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.UNAUTHORIZED.getCode())
            );
        }

        @DisplayName("브랜드명이 빈 값이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenNameIsBlank() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("", "https://example.com/logo.png", "브랜드 설명");

            // act
            var response = createBrandRequest(request, adminHeaders());

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @DisplayName("로고 URL이 빈 값이면, 400 BAD_REQUEST 응답을 받는다.")
        @Test
        void returnsBadRequest_whenLogoUrlIsBlank() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("브랜드명", "", "브랜드 설명");

            // act
            var response = createBrandRequest(request, adminHeaders());

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.BAD_REQUEST.getCode())
            );
        }

        @DisplayName("브랜드명이 1자이면, INVALID_BRAND_NAME 에러 응답을 받는다.")
        @Test
        void returnsInvalidBrandName_whenNameIsTooShort() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("X", "https://example.com/logo.png", "브랜드 설명");

            // act
            var response = createBrandRequest(request, adminHeaders());

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.INVALID_BRAND_NAME.getCode())
            );
        }

        @DisplayName("브랜드명이 50자 초과이면, INVALID_BRAND_NAME 에러 응답을 받는다.")
        @Test
        void returnsInvalidBrandName_whenNameIsTooLong() {
            // arrange
            var name = "a".repeat(51);
            var request = new BrandDto.CreateBrandRequest(name, "https://example.com/logo.png", "브랜드 설명");

            // act
            var response = createBrandRequest(request, adminHeaders());

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.INVALID_BRAND_NAME.getCode())
            );
        }

        @DisplayName("이미 존재하는 브랜드명으로 등록하면, ALREADY_EXIST_BRAND_NAME 에러 응답을 받는다.")
        @Test
        void returnsAlreadyExistBrandName_whenDuplicateNameProvided() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "브랜드 설명");
            createBrandRequest(request, adminHeaders());

            // act
            var response = createBrandRequest(request, adminHeaders());

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().meta().errorCode()).isEqualTo(ErrorType.ALREADY_EXIST_BRAND_NAME.getCode())
            );
        }
    }

    private HttpHeaders adminHeaders() {
        var headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        return headers;
    }

    private ResponseEntity<ApiResponse<BrandDto.CreateBrandResponse>> createBrandRequest(
            BrandDto.CreateBrandRequest request, HttpHeaders headers) {
        ParameterizedTypeReference<ApiResponse<BrandDto.CreateBrandResponse>> responseType = new ParameterizedTypeReference<>() {};
        return testRestTemplate.exchange(
                BRAND_ADMIN_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, headers), responseType);
    }
}

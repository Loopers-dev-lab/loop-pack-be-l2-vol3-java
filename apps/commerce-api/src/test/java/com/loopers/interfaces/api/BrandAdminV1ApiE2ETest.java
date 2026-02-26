package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.BrandV1Dto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
@DisplayName("Brand Admin API E2E 테스트")
class BrandAdminV1ApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api-admin/v1/brands - 브랜드 등록")
    class RegisterBrand {

        @Test
        @DisplayName("성공: 유효한 브랜드 정보로 등록")
        void registerBrand_Success() {
            // Given
            BrandV1Dto.RegisterRequest request = new BrandV1Dto.RegisterRequest(
                    "샤넬",
                    "프랑스 명품 브랜드",
                    "https://example.com/chanel.png"
            );

            // When
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.Response>> responseType = 
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/brands",
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    responseType
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().data()).isNotNull();
            assertThat(response.getBody().data().name()).isEqualTo("샤넬");
            assertThat(response.getBody().data().description()).isEqualTo("프랑스 명품 브랜드");
            assertThat(response.getBody().data().logoUrl()).isEqualTo("https://example.com/chanel.png");
            assertThat(response.getBody().data().createdAt()).isNotNull();
            assertThat(response.getBody().data().updatedAt()).isNotNull();
        }

        @Test
        @DisplayName("실패: 브랜드명이 null")
        void registerBrand_NameIsNull() {
            // Given
            BrandV1Dto.RegisterRequest request = new BrandV1Dto.RegisterRequest(
                    null,
                    "프랑스 명품 브랜드",
                    null
            );

            // When
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.Response>> responseType = 
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/brands",
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    responseType
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("실패: 브랜드명 중복")
        void registerBrand_DuplicateName() {
            // Given
            BrandV1Dto.RegisterRequest request = new BrandV1Dto.RegisterRequest("샤넬", null, null);

            ParameterizedTypeReference<ApiResponse<BrandV1Dto.Response>> responseType = 
                    new ParameterizedTypeReference<>() {};
            restTemplate.exchange(
                    "/api-admin/v1/brands",
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    responseType
            );

            // When
            ResponseEntity<ApiResponse<BrandV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/brands",
                    HttpMethod.POST,
                    new HttpEntity<>(request),
                    responseType
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/brands/{brandId} - 브랜드 조회")
    class GetBrand {

        @Test
        @DisplayName("성공: 유효한 브랜드 ID로 조회")
        void getBrand_Success() {
            // Given
            BrandV1Dto.RegisterRequest registerRequest = new BrandV1Dto.RegisterRequest(
                    "샤넬",
                    "프랑스 명품 브랜드",
                    "https://example.com/chanel.png"
            );

            ParameterizedTypeReference<ApiResponse<BrandV1Dto.Response>> responseType = 
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.Response>> registerResponse = restTemplate.exchange(
                    "/api-admin/v1/brands",
                    HttpMethod.POST,
                    new HttpEntity<>(registerRequest),
                    responseType
            );

            Long brandId = registerResponse.getBody().data().id();

            // When
            ResponseEntity<ApiResponse<BrandV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/brands/" + brandId,
                    HttpMethod.GET,
                    null,
                    responseType
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().id()).isEqualTo(brandId);
            assertThat(response.getBody().data().name()).isEqualTo("샤넬");
            assertThat(response.getBody().data().description()).isEqualTo("프랑스 명품 브랜드");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 브랜드 ID")
        void getBrand_NotFound() {
            // When
            ParameterizedTypeReference<ApiResponse<BrandV1Dto.Response>> responseType = 
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/brands/999",
                    HttpMethod.GET,
                    null,
                    responseType
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("PUT /api-admin/v1/brands/{brandId} - 브랜드 수정")
    class UpdateBrand {

        @Test
        @DisplayName("성공: 브랜드 정보 수정")
        void updateBrand_Success() {
            // Given
            BrandV1Dto.RegisterRequest registerRequest = new BrandV1Dto.RegisterRequest("샤넬", "설명", null);

            ParameterizedTypeReference<ApiResponse<BrandV1Dto.Response>> responseType = 
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.Response>> registerResponse = restTemplate.exchange(
                    "/api-admin/v1/brands",
                    HttpMethod.POST,
                    new HttpEntity<>(registerRequest),
                    responseType
            );

            Long brandId = registerResponse.getBody().data().id();

            BrandV1Dto.UpdateRequest updateRequest = new BrandV1Dto.UpdateRequest(
                    "샤넬 업데이트",
                    "새로운 설명",
                    "https://example.com/new.png"
            );

            // When
            ResponseEntity<ApiResponse<BrandV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/brands/" + brandId,
                    HttpMethod.PUT,
                    new HttpEntity<>(updateRequest),
                    responseType
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody().data().name()).isEqualTo("샤넬 업데이트");
            assertThat(response.getBody().data().description()).isEqualTo("새로운 설명");
        }
    }

    @Nested
    @DisplayName("DELETE /api-admin/v1/brands/{brandId} - 브랜드 삭제")
    class DeleteBrand {

        @Test
        @DisplayName("성공: 브랜드 삭제")
        void deleteBrand_Success() {
            // Given
            BrandV1Dto.RegisterRequest registerRequest = new BrandV1Dto.RegisterRequest("샤넬", null, null);

            ParameterizedTypeReference<ApiResponse<BrandV1Dto.Response>> responseType = 
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandV1Dto.Response>> registerResponse = restTemplate.exchange(
                    "/api-admin/v1/brands",
                    HttpMethod.POST,
                    new HttpEntity<>(registerRequest),
                    responseType
            );

            Long brandId = registerResponse.getBody().data().id();

            // When
            ParameterizedTypeReference<ApiResponse<Void>> deleteResponseType = 
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api-admin/v1/brands/" + brandId,
                    HttpMethod.DELETE,
                    null,
                    deleteResponseType
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 삭제 후 조회 시 404
            ResponseEntity<ApiResponse<BrandV1Dto.Response>> getResponse = restTemplate.exchange(
                    "/api-admin/v1/brands/" + brandId,
                    HttpMethod.GET,
                    null,
                    responseType
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}

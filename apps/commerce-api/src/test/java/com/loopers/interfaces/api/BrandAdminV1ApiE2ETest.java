package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandAdminV1ApiE2ETest {

    private static final String ENDPOINT_BRANDS = "/api-admin/v1/brands";

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

    @DisplayName("GET /api-admin/v1/brands - 브랜드 목록 조회")
    @Nested
    class GetAll {

        @DisplayName("브랜드가 존재하면, 브랜드 목록을 반환한다.")
        @Test
        void returnsBrandList_whenBrandsExist() {
            // arrange
            brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            brandJpaRepository.save(new BrandModel("아디다스", "독일 스포츠 브랜드"));

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "?page=0&size=20",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat((List<?>) response.getBody().data().get("content")).hasSize(2)
            );
        }

        @DisplayName("삭제된 브랜드는 목록에 포함되지 않는다.")
        @Test
        void excludesDeletedBrands() {
            // arrange
            brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            BrandModel deletedBrand = new BrandModel("삭제브랜드", "삭제될 브랜드");
            deletedBrand.delete();
            brandJpaRepository.save(deletedBrand);

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "?page=0&size=20",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat((List<?>) response.getBody().data().get("content")).hasSize(1)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/brands/{brandId} - 브랜드 상세 조회")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드 ID가 주어지면, 브랜드 상세 정보를 반환한다.")
        @Test
        void returnsBrand_whenIdExists() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));

            // act
            ParameterizedTypeReference<ApiResponse<BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "/" + brand.getId(),
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("스포츠 의류 및 신발 브랜드")
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID가 주어지면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenIdDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "/999",
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("POST /api-admin/v1/brands - 브랜드 등록")
    @Nested
    class Register {

        @DisplayName("정상적인 정보가 주어지면, 브랜드가 등록된다.")
        @Test
        void registersBrand_whenValidInfoIsProvided() {
            // arrange
            RegisterRequest request = new RegisterRequest("나이키", "스포츠 의류 및 신발 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_BRANDS,
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("스포츠 의류 및 신발 브랜드"),
                () -> assertThat(response.getBody().data().id()).isNotNull()
            );
        }

        @DisplayName("이미 존재하는 이름으로 등록하면, CONFLICT 응답을 받는다.")
        @Test
        void returnsConflict_whenNameAlreadyExists() {
            // arrange
            brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            RegisterRequest request = new RegisterRequest("나이키", "다른 설명");

            // act
            ParameterizedTypeReference<ApiResponse<BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_BRANDS,
                HttpMethod.POST,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @DisplayName("PUT /api-admin/v1/brands/{brandId} - 브랜드 수정")
    @Nested
    class Update {

        @DisplayName("정상적인 정보가 주어지면, 브랜드가 수정된다.")
        @Test
        void updatesBrand_whenValidInfoIsProvided() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));
            UpdateRequest request = new UpdateRequest("아디다스", "독일 스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "/" + brand.getId(),
                HttpMethod.PUT,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("아디다스"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("독일 스포츠 브랜드")
            );
        }

        @DisplayName("존재하지 않는 브랜드 ID가 주어지면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenIdDoesNotExist() {
            // arrange
            UpdateRequest request = new UpdateRequest("아디다스", "독일 스포츠 브랜드");

            // act
            ParameterizedTypeReference<ApiResponse<BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "/999",
                HttpMethod.PUT,
                new HttpEntity<>(request),
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api-admin/v1/brands/{brandId} - 브랜드 삭제")
    @Nested
    class Delete {

        @DisplayName("존재하는 브랜드 ID가 주어지면, 브랜드가 삭제된다.")
        @Test
        void deletesBrand_whenIdExists() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠 의류 및 신발 브랜드"));

            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "/" + brand.getId(),
                HttpMethod.DELETE,
                null,
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // verify soft delete - brand should not be found via API
            ParameterizedTypeReference<ApiResponse<BrandResponse>> getResponseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandResponse>> getResponse = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "/" + brand.getId(),
                HttpMethod.GET,
                null,
                getResponseType
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("존재하지 않는 브랜드 ID가 주어지면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenIdDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<Void>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "/999",
                HttpMethod.DELETE,
                null,
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    record RegisterRequest(
        String name,
        String description
    ) {}

    record UpdateRequest(
        String name,
        String description
    ) {}

    record BrandResponse(
        Long id,
        String name,
        String description,
        String createdAt,
        String updatedAt
    ) {}
}

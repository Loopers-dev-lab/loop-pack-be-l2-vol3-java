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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String ENDPOINT_BRANDS = "/api/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandV1ApiE2ETest(
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

    @DisplayName("GET /api/v1/brands/{brandId} - 브랜드 조회")
    @Nested
    class GetBrand {

        @DisplayName("존재하는 브랜드 ID가 주어지면, 브랜드 정보를 반환한다.")
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

        @DisplayName("삭제된 브랜드 ID가 주어지면, NOT_FOUND 응답을 받는다.")
        @Test
        void returnsNotFound_whenBrandIsDeleted() {
            // arrange
            BrandModel brand = new BrandModel("나이키", "스포츠 의류 및 신발 브랜드");
            brand.delete();
            brandJpaRepository.save(brand);

            // act
            ParameterizedTypeReference<ApiResponse<BrandResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<BrandResponse>> response = testRestTemplate.exchange(
                ENDPOINT_BRANDS + "/" + brand.getId(),
                HttpMethod.GET,
                null,
                responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    record BrandResponse(
        Long id,
        String name,
        String description,
        String createdAt,
        String updatedAt
    ) {}
}

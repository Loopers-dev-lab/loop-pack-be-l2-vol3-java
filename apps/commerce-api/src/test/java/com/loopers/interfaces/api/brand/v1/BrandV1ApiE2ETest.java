package com.loopers.interfaces.api.brand.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.Map;

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
import org.springframework.util.LinkedMultiValueMap;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.brand.v1.BrandDto.CreateBrandResponse;
import com.loopers.utils.DatabaseCleanUp;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String BRAND_ENDPOINT = "/api/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final BrandRepository brandRepository;

    @Autowired
    public BrandV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            BrandRepository brandRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.brandRepository = brandRepository;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetActiveBrand {

        @DisplayName("활성 브랜드가 존재하면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenBrandIsActive() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "브랜드 설명");
            var result = createBrandRequest(request);
            var brandId = result.getBody().data().brandId();

            // act
            var response = testRestTemplate.exchange(
                    BRAND_ENDPOINT + "/" + brandId,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ApiResponse<BrandDto.BrandResponse>>() {
                    }
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody()).isNotNull(),
                    () -> assertThat(response.getBody().data().id()).isEqualTo(brandId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo(request.name()),
                    () -> assertThat(response.getBody().data().logoUrl()).isEqualTo(request.logoUrl()),
                    () -> assertThat(response.getBody().data().description()).isEqualTo(request.description())
            );
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면, 404를 반환한다.")
        @Test
        void returns404_whenBrandNotFound() {
            // act
            var response = testRestTemplate.exchange(
                    BRAND_ENDPOINT + "/999",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ApiResponse<BrandDto.BrandResponse>>() {
                    }
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("삭제된 브랜드를 조회하면, 404를 반환한다.")
        @Test
        void returns404_whenBrandIsDeleted() {
            // arrange
            Brand brand = Brand.create("삭제브랜드", "https://example.com/logo.png", "설명");
            brand.delete();
            brandRepository.save(brand);

            // act
            var response = testRestTemplate.exchange(
                    BRAND_ENDPOINT + "/" + brand.getId(),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ApiResponse<BrandDto.BrandResponse>>() {
                    }
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    private ResponseEntity<ApiResponse<CreateBrandResponse>> createBrandRequest(
            BrandDto.CreateBrandRequest request
    ) {
        return testRestTemplate.exchange(
                "/api-admin/v1/brands",
                HttpMethod.POST,
                new HttpEntity<>(request, new HttpHeaders(new LinkedMultiValueMap<>(Map.of("X-Loopers-Ldap", List.of("loopers.admin"))))),
                new ParameterizedTypeReference<ApiResponse<CreateBrandResponse>>() {
                }
        );
    }
}

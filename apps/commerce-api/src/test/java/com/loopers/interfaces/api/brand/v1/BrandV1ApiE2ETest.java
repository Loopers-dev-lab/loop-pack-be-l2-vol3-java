package com.loopers.interfaces.api.brand.v1;

import static com.loopers.interfaces.api.brand.v1.BrandSteps.createBrand;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.BaseE2ETest;

class BrandV1ApiE2ETest extends BaseE2ETest {

    private static final String BRAND_ENDPOINT = "/api/v1/brands";

    @Autowired
    private BrandRepository brandRepository;

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class GetActiveBrand {

        @DisplayName("활성 브랜드가 존재하면, 브랜드 정보를 반환한다.")
        @Test
        void returnsBrand_whenBrandIsActive() {
            // arrange
            var request = new BrandDto.CreateBrandRequest("브랜드명", "https://example.com/logo.png", "브랜드 설명");
            var brandId = createBrand(testRestTemplate, request);

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
}

package com.loopers.interfaces.api.brand;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.BrandStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BrandApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Brand createActiveBrand(String name, String description) {
        return brandRepository.save(Brand.create(name, description));
    }

    @DisplayName("GET /api/v1/brands")
    @Nested
    class 브랜드_목록_조회 {

        @Test
        void ACTIVE_브랜드만_조회되고_200_OK를_반환한다() {
            // arrange
            createActiveBrand("나이키", "스포츠 브랜드");
            createActiveBrand("아디다스", "독일 브랜드");

            Brand inactive = Brand.create("비활성", "설명");
            inactive.changeStatus(BrandStatus.INACTIVE);
            brandRepository.save(inactive);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity("/api/v1/brands", ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 브랜드가_없으면_빈_목록과_200_OK를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity("/api/v1/brands", ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @DisplayName("GET /api/v1/brands/{brandId}")
    @Nested
    class 브랜드_상세_조회 {

        @Test
        void 존재하는_ACTIVE_브랜드면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키", "스포츠 브랜드");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity(
                    "/api/v1/brands/" + brand.getId(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 존재하지_않는_ID면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity(
                    "/api/v1/brands/999", ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void INACTIVE_브랜드면_404_Not_Found를_반환한다() {
            // arrange
            Brand brand = Brand.create("비활성", "설명");
            brand.changeStatus(BrandStatus.INACTIVE);
            brandRepository.save(brand);

            // act - 고객에게 비활성 브랜드는 "존재하지 않음"과 동일
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity(
                    "/api/v1/brands/" + brand.getId(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 삭제된_브랜드면_404_Not_Found를_반환한다() {
            // arrange
            Brand brand = Brand.create("삭제됨", "설명");
            brand.delete();
            brandRepository.save(brand);

            // act - 고객에게 삭제된 브랜드는 "존재하지 않음"과 동일
            ResponseEntity<ApiResponse> response = testRestTemplate.getForEntity(
                    "/api/v1/brands/" + brand.getId(), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}

package com.loopers.interfaces.api;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.model.BrandCommand;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.infrastructure.brand.entity.BrandEntity;
import com.loopers.infrastructure.brand.repository.BrandJpaRepository;
import com.loopers.infrastructure.product.entity.ProductEntity;
import com.loopers.infrastructure.product.repository.ProductJpaRepository;
import com.loopers.interfaces.api.brand.dto.FindBrandApiResDto;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandV1ApiE2ETest {

    private static final String ENDPOINT_BRANDS = "/api/v1/brands";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public BrandV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private BrandEntity saveBrand(String name, String description) {
        Brand brand = Brand.create(new BrandCommand.Create(name, description));
        return brandJpaRepository.save(BrandEntity.toEntity(brand));
    }

    private ProductEntity saveProduct(Long brandId, String name, int price, int stock) {
        Product product = Product.create(brandId, new ProductCommand.Create(brandId, name, price, stock));
        return productJpaRepository.save(ProductEntity.toEntity(product));
    }

    @DisplayName("GET /api/v1/brands - 브랜드 목록 조회")
    @Nested
    class FindBrandList {

        @DisplayName("브랜드가 없으면 빈 페이지를 반환한다")
        @Test
        void findBrandList_empty() {
            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.GET, null, responseType);

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());

            List<?> content = (List<?>) response.getBody().data().get("content");
            assertThat(content).isEmpty();
        }

        @DisplayName("브랜드가 여러 건 있으면 페이지 content에 모두 포함된다")
        @Test
        void findBrandList_multipleItems() {
            // arrange
            saveBrand("나이키", "나이키 설명");
            saveBrand("아디다스", "아디다스 설명");
            saveBrand("뉴발란스", "뉴발란스 설명");

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS, HttpMethod.GET, null, responseType);

            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());

            List<?> content = (List<?>) response.getBody().data().get("content");
            assertThat(content).hasSize(3);
        }
    }

    @DisplayName("GET /api/v1/brands/{brandId} - 브랜드 상세 조회")
    @Nested
    class FindBrand {

        @DisplayName("존재하지 않는 브랜드 ID로 조회하면 404 NOT_FOUND 응답을 받는다")
        @Test
        void findBrand_notFound() {
            // act
            ParameterizedTypeReference<ApiResponse<FindBrandApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindBrandApiResDto>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS + "/99999", HttpMethod.GET, null, responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @DisplayName("상품이 없는 브랜드를 조회하면 products가 빈 배열로 반환된다")
        @Test
        void findBrand_noProducts() {
            // arrange
            BrandEntity savedBrand = saveBrand("나이키", "나이키 설명");

            // act
            ParameterizedTypeReference<ApiResponse<FindBrandApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindBrandApiResDto>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS + "/" + savedBrand.getId(), HttpMethod.GET, null, responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(savedBrand.getId()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().description()).isEqualTo("나이키 설명"),
                () -> assertThat(response.getBody().data().products()).isEmpty()
            );
        }

        @DisplayName("상품이 있는 브랜드를 조회하면 products 목록이 포함된다")
        @Test
        void findBrand_withProducts() {
            // arrange
            BrandEntity savedBrand = saveBrand("나이키", "나이키 설명");
            saveProduct(savedBrand.getId(), "에어맥스", 150000, 10);
            saveProduct(savedBrand.getId(), "에어포스", 120000, 5);

            // act
            ParameterizedTypeReference<ApiResponse<FindBrandApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindBrandApiResDto>> response =
                testRestTemplate.exchange(ENDPOINT_BRANDS + "/" + savedBrand.getId(), HttpMethod.GET, null, responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().id()).isEqualTo(savedBrand.getId()),
                () -> assertThat(response.getBody().data().products()).hasSize(2)
            );
        }
    }
}

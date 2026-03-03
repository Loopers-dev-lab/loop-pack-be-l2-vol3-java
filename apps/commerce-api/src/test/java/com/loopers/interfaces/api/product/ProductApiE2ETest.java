package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import java.util.UUID;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class ProductApiE2ETest {

    private static final String ENDPOINT_PRODUCTS = "/api/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final BrandRepository brandRepository;
    private final CategoryRepository categoryRepository;

    @Autowired
    public ProductApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            BrandRepository brandRepository,
            CategoryRepository categoryRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("상품 API")
    class ProductApi {

        @Test
        @DisplayName("상품 생성 후 상세 조회에 성공한다")
        void createAndGetDetail() {
            UUID categoryId = createCategory("푸드");
            UUID brandId = createBrand("퍼피박스");
            ProductDto.CreateProductRequest create = new ProductDto.CreateProductRequest(
                    "강아지 샴푸",
                    8900,
                    50,
                    "저자극",
                    categoryId,
                    brandId
            );

            ResponseEntity<ApiResponse<ProductDto.ProductResponse>> created = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS,
                    HttpMethod.POST,
                    new HttpEntity<>(create),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            UUID productId = created.getBody().data().id();

            ResponseEntity<ApiResponse<ProductDto.ProductResponse>> detail = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "/" + productId,
                    HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(detail.getBody().data().name()).isEqualTo("강아지 샴푸");
        }

        @Test
        @DisplayName("브랜드 필터로 목록 조회에 성공한다")
        void listWithBrandFilter() {
            UUID categoryId = createCategory("푸드");
            UUID brandIdForList = createBrand("퍼피박스");
            UUID otherBrandId = createBrand("포메피아");
            create("상품A", 1000, categoryId, brandIdForList);
            create("상품B", 2000, categoryId, otherBrandId);

            ResponseEntity<ApiResponse<ProductDto.ProductListResponse>> list = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "?brandId=" + brandIdForList + "&sort=latest&page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    }
                );

            assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(list.getBody().data().totalElements()).isEqualTo(1);
            assertThat(list.getBody().data().items().get(0).brandId()).isEqualTo(brandIdForList);
        }
    }

    private void create(String name, int price, UUID categoryId, UUID brandId) {
        ProductDto.CreateProductRequest request = new ProductDto.CreateProductRequest(
                name,
                price,
                10,
                "desc",
                categoryId,
                brandId
        );

        testRestTemplate.exchange(
                ENDPOINT_PRODUCTS,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<ProductDto.ProductResponse>>() {
                }
        );
    }

    private UUID createCategory(String name) {
        return categoryRepository.save(new Category(name)).id();
    }

    private UUID createBrand(String name) {
        return brandRepository.save(new Brand(new BrandName(name), "", "")).id();
    }
}

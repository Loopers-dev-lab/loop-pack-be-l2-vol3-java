package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.brand.vo.BrandName;
import com.loopers.domain.category.Category;
import com.loopers.domain.category.CategoryRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
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
    private final ProductRepository productRepository;

    @Autowired
    public ProductApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            BrandRepository brandRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.brandRepository = brandRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
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
            UUID otherCategoryId = createCategory("위생");
            UUID brandIdForList = createBrand("퍼피박스");
            UUID otherBrandId = createBrand("포메피아");
            create("상품A", 1000, categoryId, brandIdForList);
            create("상품C", 3000, categoryId, brandIdForList);
            create("상품D", 1000, otherCategoryId, brandIdForList);
            create("상품B", 2000, categoryId, otherBrandId);

            ResponseEntity<ApiResponse<ProductDto.PublicProductListResponse>> list = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "?brandId=" + brandIdForList
                            + "&categoryId=" + categoryId
                            + "&minPrice=500&maxPrice=1500&sort=price&page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    }
                );

            assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(list.getBody().data().totalElements()).isNull();
            assertThat(list.getBody().data().items().get(0).brandId()).isEqualTo(brandIdForList);
            assertThat(list.getBody().data().items().get(0).categoryId()).isEqualTo(categoryId);
            assertThat(list.getBody().data().hasNext()).isFalse();
            assertThat(list.getBody().data().nextCursor()).isNull();
        }

        @Test
        @DisplayName("유저 목록 조회에서 삭제 조건을 주면 에러 메시지를 반환한다")
        void listWithDeletedFilterFails() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "?deleted=true",
                    HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
            assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL);
            assertThat(response.getBody().meta().message()).isEqualTo("삭제 상품 조회 조건은 관리자만 사용할 수 있습니다.");
        }

        @Test
        @DisplayName("likes 정렬은 커서 기반 목록 응답을 지원한다")
        void listWithCursorPaging() {
            UUID categoryId = createCategory("푸드");
            UUID brandId = createBrand("퍼피박스");
            UUID first = createAndReturnId("상품A", 1000, categoryId, brandId);
            UUID second = createAndReturnId("상품B", 2000, categoryId, brandId);
            UUID third = createAndReturnId("상품C", 3000, categoryId, brandId);

            productRepository.save(new Product(first, "상품A", 1000, 10, "desc", categoryId, brandId, 7, null));
            productRepository.save(new Product(second, "상품B", 2000, 10, "desc", categoryId, brandId, 5, null));
            productRepository.save(new Product(third, "상품C", 3000, 10, "desc", categoryId, brandId, 1, null));

            ResponseEntity<ApiResponse<ProductDto.PublicProductListResponse>> list = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "?brandId=" + brandId + "&sort=likes&size=2",
                    HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(list.getBody().data().page()).isNull();
            assertThat(list.getBody().data().totalElements()).isNull();
            assertThat(list.getBody().data().hasNext()).isTrue();
            assertThat(list.getBody().data().nextCursor()).isNotBlank();
            assertThat(list.getBody().data().items()).hasSize(2);
            assertThat(list.getBody().data().items().get(0).name()).isEqualTo("상품A");
            assertThat(list.getBody().data().items().get(1).name()).isEqualTo("상품B");
        }

        @Test
        @DisplayName("cursor를 전달하면 다음 페이지를 조회한다")
        void listWithCursorToken() {
            UUID categoryId = createCategory("푸드");
            UUID brandId = createBrand("퍼피박스");
            UUID first = createAndReturnId("상품A", 1000, categoryId, brandId);
            UUID second = createAndReturnId("상품B", 2000, categoryId, brandId);
            UUID third = createAndReturnId("상품C", 3000, categoryId, brandId);

            productRepository.save(new Product(first, "상품A", 1000, 10, "desc", categoryId, brandId, 7, null));
            productRepository.save(new Product(second, "상품B", 2000, 10, "desc", categoryId, brandId, 5, null));
            productRepository.save(new Product(third, "상품C", 3000, 10, "desc", categoryId, brandId, 1, null));

            ResponseEntity<ApiResponse<ProductDto.PublicProductListResponse>> firstPage = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "?brandId=" + brandId + "&sort=likes&size=2",
                    HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    }
            );

            String cursor = firstPage.getBody().data().nextCursor();

            ResponseEntity<ApiResponse<ProductDto.PublicProductListResponse>> secondPage = testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "?brandId=" + brandId + "&sort=likes&size=2&cursor=" + cursor,
                    HttpMethod.GET,
                    new HttpEntity<>(null),
                    new ParameterizedTypeReference<>() {
                    }
            );

            assertThat(secondPage.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(secondPage.getBody().data().items()).hasSize(1);
            assertThat(secondPage.getBody().data().items().get(0).name()).isEqualTo("상품C");
            assertThat(secondPage.getBody().data().hasNext()).isFalse();
        }
    }

    private void create(String name, int price, UUID categoryId, UUID brandId) {
        createAndReturnId(name, price, categoryId, brandId);
    }

    private UUID createAndReturnId(String name, int price, UUID categoryId, UUID brandId) {
        ProductDto.CreateProductRequest request = new ProductDto.CreateProductRequest(
                name,
                price,
                10,
                "desc",
                categoryId,
                brandId
        );

        ResponseEntity<ApiResponse<ProductDto.ProductResponse>> response = testRestTemplate.exchange(
                ENDPOINT_PRODUCTS,
                HttpMethod.POST,
                new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<ProductDto.ProductResponse>>() {
                }
        );
        return response.getBody().data().id();
    }

    private UUID createCategory(String name) {
        return categoryRepository.save(new Category(name)).id();
    }

    private UUID createBrand(String name) {
        return brandRepository.save(new Brand(new BrandName(name), "", "")).id();
    }
}

package com.loopers.interfaces.api;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.stock.StockModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.interfaces.api.product.ProductAdminV1Dto;
import com.loopers.utils.DatabaseCleanUp;
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

import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductAdminV1ApiE2ETest {

    private static final String ENDPOINT_BASE = "/api-admin/v1/products";
    private static final Function<Long, String> ENDPOINT_BY_ID = id -> ENDPOINT_BASE + "/" + id;
    private static final Function<Long, String> ENDPOINT_STOCK = id -> ENDPOINT_BASE + "/" + id + "/stock";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final StockJpaRepository stockJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductAdminV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        StockJpaRepository stockJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.stockJpaRepository = stockJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-AdminLdap", "admin");
        return headers;
    }

    @DisplayName("POST /api-admin/v1/products")
    @Nested
    class Create {

        @DisplayName("유효한 요청이면 상품을 등록하고 200을 반환한다")
        @Test
        void createsSuccessfully() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠"));
            ProductAdminV1Dto.CreateRequest request = new ProductAdminV1Dto.CreateRequest(
                "에어맥스", "러닝화", 129000, brand.getId(), 100
            );
            HttpEntity<ProductAdminV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());
            // act
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductAdminDetailResponse>> response = testRestTemplate.exchange(
                ENDPOINT_BASE, HttpMethod.POST, entity,
                new ParameterizedTypeReference<>() {}
            );
            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().name()).isEqualTo("에어맥스"),
                () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(100)
            );
        }
    }

    @DisplayName("PATCH /api-admin/v1/products/{id}/stock")
    @Nested
    class UpdateStock {

        @DisplayName("재고를 수정하고 200을 반환한다")
        @Test
        void updatesStockSuccessfully() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠"));
            ProductModel product = productJpaRepository.save(
                new ProductModel("에어맥스", "러닝화", new Money(129000), brand.getId())
            );
            stockJpaRepository.save(new StockModel(product.getId(), 100));
            ProductAdminV1Dto.UpdateStockRequest request = new ProductAdminV1Dto.UpdateStockRequest(50);
            HttpEntity<ProductAdminV1Dto.UpdateStockRequest> entity = new HttpEntity<>(request, adminHeaders());
            // act
            ResponseEntity<ApiResponse<ProductAdminV1Dto.ProductAdminDetailResponse>> response = testRestTemplate.exchange(
                ENDPOINT_STOCK.apply(product.getId()), HttpMethod.PATCH, entity,
                new ParameterizedTypeReference<>() {}
            );
            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().stockQuantity()).isEqualTo(50)
            );
        }
    }

    @DisplayName("DELETE /api-admin/v1/products/{id}")
    @Nested
    class Delete {

        @DisplayName("상품을 삭제하면 200을 반환한다")
        @Test
        void deletesSuccessfully() {
            // arrange
            BrandModel brand = brandJpaRepository.save(new BrandModel("나이키", "스포츠"));
            ProductModel product = productJpaRepository.save(
                new ProductModel("에어맥스", "러닝화", new Money(129000), brand.getId())
            );
            stockJpaRepository.save(new StockModel(product.getId(), 100));
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());
            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_BY_ID.apply(product.getId()), HttpMethod.DELETE, entity,
                new ParameterizedTypeReference<>() {}
            );
            // assert
            assertTrue(response.getStatusCode().is2xxSuccessful());
        }
    }
}

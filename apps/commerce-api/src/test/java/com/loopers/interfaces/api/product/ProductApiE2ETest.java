package com.loopers.interfaces.api.product;

import com.loopers.interfaces.api.brand.dto.BrandCreateRequest;
import com.loopers.interfaces.api.common.PageResponse;
import com.loopers.interfaces.api.product.dto.ProductCreateRequest;
import com.loopers.interfaces.api.product.dto.ProductDetailResponse;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class ProductApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private static final String ADMIN_URL = "/api-admin/v1/products";
    private static final String PUBLIC_URL = "/api/v1/products";
    private static final String BRAND_ADMIN_URL = "/api-admin/v1/brands";

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("E2E: 상품 CRUD 시나리오")
    class ProductCrudE2E {

        @Test
        @DisplayName("상품 생성 → 상세 조회 성공")
        void create_then_getDetail() {
            // given
            createBrand("나이키", "스포츠");
            var request = new ProductCreateRequest(1L, "운동화", 50000, null, 100, "좋은 운동화");

            // when - 생성
            ResponseEntity<Void> createResponse = restTemplate.exchange(
                    ADMIN_URL,
                    HttpMethod.POST,
                    new HttpEntity<>(request, createAdminHeaders()),
                    Void.class
            );
            assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // when - 조회
            ResponseEntity<ProductDetailResponse> getResponse = restTemplate.getForEntity(
                    PUBLIC_URL + "/1",
                    ProductDetailResponse.class
            );

            // then
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(getResponse.getBody()).isNotNull();
            assertThat(getResponse.getBody().name()).isEqualTo("운동화");
            assertThat(getResponse.getBody().brandName()).isEqualTo("나이키");
            assertThat(getResponse.getBody().price()).isEqualTo(50000);
        }

        @Test
        @DisplayName("상품 목록 조회 (페이징)")
        void getProductList() {
            // given
            createBrand("나이키", "스포츠");
            createProduct(1L, "운동화", 50000, 100);
            createProduct(1L, "슬리퍼", 30000, 200);

            // when
            ResponseEntity<String> response = restTemplate.getForEntity(
                    PUBLIC_URL + "?page=0&size=20",
                    String.class
            );

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).contains("운동화");
            assertThat(response.getBody()).contains("슬리퍼");
        }

        @Test
        @DisplayName("상품 생성 → 삭제 → 조회 실패")
        void create_delete_then_getFail() {
            // given
            createBrand("나이키", "스포츠");
            createProduct(1L, "운동화", 50000, 100);

            // when - 삭제
            ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                    ADMIN_URL + "/1",
                    HttpMethod.DELETE,
                    new HttpEntity<>(createAdminHeaders()),
                    Void.class
            );
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // then - 삭제된 상품 조회 실패
            ResponseEntity<String> getResponse = restTemplate.getForEntity(
                    PUBLIC_URL + "/1",
                    String.class
            );
            assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("E2E: 브랜드 삭제 cascade 시나리오")
    class BrandDeleteCascadeE2E {

        @Test
        @DisplayName("브랜드 삭제 시 하위 상품도 삭제됨")
        void deleteBrand_cascadeProducts() {
            // given
            createBrand("나이키", "스포츠");
            createProduct(1L, "운동화", 50000, 100);
            createProduct(1L, "슬리퍼", 30000, 200);

            // when - 브랜드 삭제
            ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                    BRAND_ADMIN_URL + "/1",
                    HttpMethod.DELETE,
                    new HttpEntity<>(createAdminHeaders()),
                    Void.class
            );
            assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

            // then - 상품 조회 실패
            ResponseEntity<String> product1Response = restTemplate.getForEntity(
                    PUBLIC_URL + "/1", String.class);
            assertThat(product1Response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            ResponseEntity<String> product2Response = restTemplate.getForEntity(
                    PUBLIC_URL + "/2", String.class);
            assertThat(product2Response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private HttpHeaders createAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private void createBrand(String name, String description) {
        var request = new BrandCreateRequest(name, description);
        ResponseEntity<Void> response = restTemplate.exchange(
                BRAND_ADMIN_URL,
                HttpMethod.POST,
                new HttpEntity<>(request, createAdminHeaders()),
                Void.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void createProduct(Long brandId, String name, int price, int stock) {
        var request = new ProductCreateRequest(brandId, name, price, null, stock, "설명");
        ResponseEntity<Void> response = restTemplate.exchange(
                ADMIN_URL,
                HttpMethod.POST,
                new HttpEntity<>(request, createAdminHeaders()),
                Void.class
        );
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

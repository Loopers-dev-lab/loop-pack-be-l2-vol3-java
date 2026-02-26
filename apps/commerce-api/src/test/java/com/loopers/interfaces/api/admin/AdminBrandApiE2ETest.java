package com.loopers.interfaces.api.admin;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.OptionRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminBrandApiE2ETest {

    private static final String ADMIN_BRANDS_ENDPOINT = "/api/admin/v1/brands";
    private static final String ADMIN_LDAP = "admin-test";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private BrandRepository brandRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OptionRepository optionRepository;
    @Autowired
    private BrandJpaRepository brandJpaRepository;
    @Autowired
    private ProductJpaRepository productJpaRepository;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("POST /api/admin/v1/brands")
    class CreateBrandTest {

        @Test
        @DisplayName("브랜드를 생성할 수 있다")
        void create_success() {
            // arrange
            AdminBrandDto.CreateRequest request = new AdminBrandDto.CreateRequest("테스트 브랜드");
            HttpEntity<AdminBrandDto.CreateRequest> httpEntity = createAdminHttpEntity(request);

            // act
            ResponseEntity<ApiResponse<AdminBrandDto.Response>> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_ENDPOINT, HttpMethod.POST, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("테스트 브랜드"),
                    () -> assertThat(response.getBody().data().deleted()).isFalse()
            );
        }

        @Test
        @DisplayName("Admin 인증 없이 요청하면 401 응답을 받는다")
        void create_unauthorized() {
            // arrange
            AdminBrandDto.CreateRequest request = new AdminBrandDto.CreateRequest("테스트 브랜드");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<AdminBrandDto.CreateRequest> httpEntity = new HttpEntity<>(request, headers);

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_ENDPOINT, HttpMethod.POST, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("PUT /api/admin/v1/brands/{id}")
    class UpdateBrandTest {

        @Test
        @DisplayName("브랜드를 수정할 수 있다")
        void update_success() {
            // arrange
            Brand brand = brandRepository.save(Brand.create("원래 브랜드"));
            AdminBrandDto.UpdateRequest request = new AdminBrandDto.UpdateRequest("수정된 브랜드");
            HttpEntity<AdminBrandDto.UpdateRequest> httpEntity = createAdminHttpEntity(request);

            // act
            ResponseEntity<ApiResponse<AdminBrandDto.Response>> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_ENDPOINT + "/" + brand.getId(),
                    HttpMethod.PUT, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("수정된 브랜드")
            );
        }
    }

    @Nested
    @DisplayName("DELETE /api/admin/v1/brands/{id}")
    class DeleteBrandTest {

        @Test
        @DisplayName("브랜드를 삭제하면 해당 브랜드의 상품과 옵션도 soft delete 된다")
        void delete_cascadesProducts() {
            // arrange
            Brand brand = brandRepository.save(Brand.create("삭제될 브랜드"));
            Product product = productRepository.save(Product.create(brand.getId(), "삭제될 상품", Money.of(BigDecimal.valueOf(10000))));
            optionRepository.save(Option.create(product.getId(), "삭제될 옵션", Money.of(BigDecimal.ZERO), 10));

            HttpEntity<Void> httpEntity = createAdminHttpEntity(null);

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_ENDPOINT + "/" + brand.getId(),
                    HttpMethod.DELETE, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            Brand deletedBrand = brandJpaRepository.findById(brand.getId()).orElseThrow();
            assertThat(deletedBrand.isDeleted()).isTrue();

            Product deletedProduct = productJpaRepository.findById(product.getId()).orElseThrow();
            assertThat(deletedProduct.isDeleted()).isTrue();
        }
    }

    @Nested
    @DisplayName("GET /api/admin/v1/brands")
    class GetAllBrandsTest {

        @Test
        @DisplayName("전체 브랜드 목록을 조회할 수 있다")
        void getAll_success() {
            // arrange
            brandRepository.save(Brand.create("브랜드A"));
            brandRepository.save(Brand.create("브랜드B"));
            HttpEntity<Void> httpEntity = createAdminHttpEntity(null);

            // act
            ResponseEntity<ApiResponse<AdminBrandDto.ListResponse>> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_ENDPOINT, HttpMethod.GET, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().brands()).hasSize(2)
            );
        }
    }

    @Nested
    @DisplayName("GET /api/admin/v1/brands/{id}")
    class GetBrandByIdTest {

        @Test
        @DisplayName("ID로 브랜드를 조회할 수 있다")
        void getById_success() {
            // arrange
            Brand brand = brandRepository.save(Brand.create("조회할 브랜드"));
            HttpEntity<Void> httpEntity = createAdminHttpEntity(null);

            // act
            ResponseEntity<ApiResponse<AdminBrandDto.Response>> response = testRestTemplate.exchange(
                    ADMIN_BRANDS_ENDPOINT + "/" + brand.getId(),
                    HttpMethod.GET, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("조회할 브랜드")
            );
        }
    }

    private <T> HttpEntity<T> createAdminHttpEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-Ldap", ADMIN_LDAP);
        return new HttpEntity<>(body, headers);
    }
}

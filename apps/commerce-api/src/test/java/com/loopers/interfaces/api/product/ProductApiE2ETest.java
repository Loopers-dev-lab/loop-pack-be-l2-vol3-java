package com.loopers.interfaces.api.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductViewEvent;
import com.loopers.domain.product.ProductViewEventPublisher;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.loopers.support.page.PageResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.verify;

@Import(RedisTestContainersConfig.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductApiE2ETest {

    private static final String ENDPOINT = "/api/v1/products";
    private static final String ADMIN_ENDPOINT = "/api-admin/v1/products";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @MockBean
    private ProductViewEventPublisher productViewEventPublisher;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetList {

        @DisplayName("최초 조회 시 (캐시 MISS), 200 응답과 상품 목록을 반환하며 목록 캐시 키가 생성된다.")
        @Test
        void returnsProductList_whenCacheMiss() {
            // arrange
            Brand brand = brandJpaRepository.save(Brand.of("나이키", null));
            productJpaRepository.save(
                    Product.of("나이키 에어맥스", "편안한 운동화", Stock.from(10), Price.from(150000), brand.getId())
            );
            String url = ENDPOINT + "?brandId=" + brand.getId() + "&sort=latest&page=0&size=20";
            String listCacheKey = "product:list:brandId=" + brand.getId() + ":sort=latest:page=0:size=20";

            ParameterizedTypeReference<ApiResponse<PageResponse<ProductDto.ListResponse>>> responseType = new ParameterizedTypeReference<>() {};

            // act
            ResponseEntity<ApiResponse<PageResponse<ProductDto.ListResponse>>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("나이키 에어맥스"),
                    () -> assertThat(redisTemplate.hasKey(listCacheKey)).isTrue()
            );
        }

        @DisplayName("두 번째 조회 시 (캐시 HIT), 200 응답과 동일한 상품 목록을 반환한다.")
        @Test
        void returnsSameProductList_whenCacheHit() {
            // arrange
            Brand brand = brandJpaRepository.save(Brand.of("나이키", null));
            productJpaRepository.save(
                    Product.of("나이키 에어맥스", "편안한 운동화", Stock.from(10), Price.from(150000), brand.getId())
            );
            String url = ENDPOINT + "?brandId=" + brand.getId() + "&sort=latest&page=0&size=20";
            String listCacheKey = "product:list:brandId=" + brand.getId() + ":sort=latest:page=0:size=20";

            ParameterizedTypeReference<ApiResponse<PageResponse<ProductDto.ListResponse>>> responseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, responseType);

            // act (캐시 HIT)
            ResponseEntity<ApiResponse<PageResponse<ProductDto.ListResponse>>> response =
                    testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, responseType);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(redisTemplate.hasKey(listCacheKey)).isTrue()
            );
        }

    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetDetail {

        @DisplayName("최초 조회 시 (캐시 MISS), 200 응답과 상품 정보를 반환한다.")
        @Test
        void returnsProductInfo_whenCacheMiss() {
            // arrange
            Brand brand = brandJpaRepository.save(Brand.of("나이키", null));
            Product product = productJpaRepository.save(
                Product.of("나이키 에어맥스", "편안한 운동화", Stock.from(10), Price.from(150000), brand.getId())
            );

            // act
            ParameterizedTypeReference<ApiResponse<ProductDto.DetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductDto.DetailResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/" + product.getId(), HttpMethod.GET, HttpEntity.EMPTY, responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키 에어맥스"),
                () -> assertThat(response.getBody().data().brand()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(150000)
            );
        }

        @DisplayName("두 번째 조회 시 (캐시 HIT), 200 응답과 동일한 상품 정보를 반환한다.")
        @Test
        void returnsSameProductInfo_whenCacheHit() {
            // arrange
            Brand brand = brandJpaRepository.save(Brand.of("나이키", null));
            Product product = productJpaRepository.save(
                Product.of("나이키 에어맥스", "편안한 운동화", Stock.from(10), Price.from(150000), brand.getId())
            );
            String url = ENDPOINT + "/" + product.getId();

            ParameterizedTypeReference<ApiResponse<ProductDto.DetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, responseType);

            // act (캐시 HIT)
            ResponseEntity<ApiResponse<ProductDto.DetailResponse>> response =
                testRestTemplate.exchange(url, HttpMethod.GET, HttpEntity.EMPTY, responseType);

            // assert
            String cacheKey = "product:detail:" + product.getId();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키 에어맥스"),
                () -> assertThat(redisTemplate.hasKey(cacheKey)).isTrue()
            );
        }

        @DisplayName("상품 수정 후 조회 시, 변경된 상품 정보를 반환한다.")
        @Test
        void returnsUpdatedProductInfo_afterCacheEviction() {
            // arrange
            Brand brand = brandJpaRepository.save(Brand.of("나이키", null));
            Product product = productJpaRepository.save(
                Product.of("나이키 에어맥스", "편안한 운동화", Stock.from(10), Price.from(150000), brand.getId())
            );
            String detailUrl = ENDPOINT + "/" + product.getId();
            String updateUrl = ADMIN_ENDPOINT + "/" + product.getId();

            ParameterizedTypeReference<ApiResponse<ProductDto.DetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(detailUrl, HttpMethod.GET, HttpEntity.EMPTY, responseType);

            HttpHeaders adminHeaders = new HttpHeaders();
            adminHeaders.set("X-Loopers-Ldap", "loopers.admin");
            ProductAdminDto.UpdateRequest updateRequest =
                new ProductAdminDto.UpdateRequest("나이키 줌", "가벼운 운동화", 5, 120000);

            // act
            testRestTemplate.exchange(updateUrl, HttpMethod.PUT, new HttpEntity<>(updateRequest, adminHeaders), Void.class);
            ResponseEntity<ApiResponse<ProductDto.DetailResponse>> response =
                testRestTemplate.exchange(detailUrl, HttpMethod.GET, HttpEntity.EMPTY, responseType);

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키 줌"),
                () -> assertThat(response.getBody().data().price()).isEqualTo(120000)
            );
        }

        @DisplayName("존재하지 않는 상품 ID 로 조회 시, 404 응답을 반환한다.")
        @Test
        void returnsNotFound_whenProductNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<ProductDto.DetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<ProductDto.DetailResponse>> response =
                testRestTemplate.exchange(ENDPOINT + "/999999", HttpMethod.GET, HttpEntity.EMPTY, responseType);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("상품 조회 성공 시, ProductViewEventPublisher.publish() 가 호출된다.")
        @Test
        void publishesViewEvent_whenProductFound() {
            // arrange
            Brand brand = brandJpaRepository.save(Brand.of("나이키", null));
            Product product = productJpaRepository.save(
                Product.of("나이키 에어맥스", "편안한 운동화", Stock.from(10), Price.from(150000), brand.getId())
            );

            // act
            ParameterizedTypeReference<ApiResponse<ProductDto.DetailResponse>> responseType = new ParameterizedTypeReference<>() {};
            testRestTemplate.exchange(ENDPOINT + "/" + product.getId(), HttpMethod.GET, HttpEntity.EMPTY, responseType);

            // assert
            verify(productViewEventPublisher).publish(new ProductViewEvent.Viewed(product.getId()));
        }
    }
}

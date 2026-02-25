package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.like.Like;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserFixture;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.like.LikeJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.brand.dto.BrandV1Dto;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminBrandV1ApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/brands";
    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String LDAP_VALUE = "loopers.admin";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final LikeJpaRepository likeJpaRepository;
    private final UserJpaRepository userJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public AdminBrandV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            LikeJpaRepository likeJpaRepository,
            UserJpaRepository userJpaRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.likeJpaRepository = likeJpaRepository;
        this.userJpaRepository = userJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(LDAP_HEADER, LDAP_VALUE);
        return headers;
    }

    private Brand saveBrand(String name, String description) {
        return brandJpaRepository.save(Brand.create(name, description));
    }

    private Product saveProduct(Long brandId, String name) {
        return productJpaRepository.save(Product.create(brandId, name, null, 10000, 10));
    }

    @DisplayName("브랜드 등록 시")
    @Nested
    class CreateBrand {
        @DisplayName("유효한 요청이면, 201 Created 응답을 반환한다.")
        @Test
        void returnsCreated_whenValidRequest() {
            // arrange
            BrandV1Dto.CreateRequest request = new BrandV1Dto.CreateRequest("나이키", "스포츠 브랜드");
            HttpEntity<BrandV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.AdminBrandResponse>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키")
            );
        }

        @DisplayName("name이 누락되면, 400 Bad Request 응답을 반환한다.")
        @Test
        void returnsBadRequest_whenNameIsMissing() {
            // arrange
            BrandV1Dto.CreateRequest request = new BrandV1Dto.CreateRequest(null, "설명");
            HttpEntity<BrandV1Dto.CreateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("브랜드 상세 조회 시")
    @Nested
    class GetBrand {
        @DisplayName("존재하는 브랜드를 조회하면, 200 OK 응답을 반환한다.")
        @Test
        void returnsOk_whenBrandExists() {
            // arrange
            Brand saved = saveBrand("나이키", "스포츠 브랜드");
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.AdminBrandResponse>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + saved.getId(), HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("나이키")
            );
        }

        @DisplayName("존재하지 않는 브랜드를 조회하면, 404 Not Found 응답을 반환한다.")
        @Test
        void returnsNotFound_whenBrandNotExists() {
            // arrange
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "/999", HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("브랜드 수정 시")
    @Nested
    class UpdateBrand {
        @DisplayName("유효한 요청이면, 200 OK 응답과 수정된 브랜드를 반환한다.")
        @Test
        void returnsOk_whenValidRequest() {
            // arrange
            Brand saved = saveBrand("나이키", "스포츠 브랜드");
            BrandV1Dto.UpdateRequest request = new BrandV1Dto.UpdateRequest("아디다스", "독일 스포츠 브랜드");
            HttpEntity<BrandV1Dto.UpdateRequest> entity = new HttpEntity<>(request, adminHeaders());

            // act
            ResponseEntity<ApiResponse<BrandV1Dto.AdminBrandResponse>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + saved.getId(), HttpMethod.PUT, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().name()).isEqualTo("아디다스")
            );
        }
    }

    @DisplayName("브랜드 삭제 시")
    @Nested
    class DeleteBrand {
        @DisplayName("존재하는 브랜드를 삭제하면, 200 OK 응답을 반환하고 soft delete 처리된다.")
        @Test
        void returnsOk_andSoftDeletes() {
            // arrange
            Brand saved = saveBrand("나이키", "스포츠 브랜드");
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<Void>> response =
                    testRestTemplate.exchange(ENDPOINT + "/" + saved.getId(), HttpMethod.DELETE, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(brandJpaRepository.findById(saved.getId()).orElseThrow().getDeletedAt()).isNotNull()
            );
        }

        @DisplayName("브랜드를 삭제하면, 소속 상품도 모두 soft delete 처리된다.")
        @Test
        void softDeletesAllProducts_whenBrandIsDeleted() {
            // arrange
            Brand brand = saveBrand("나이키", "스포츠 브랜드");
            Product product1 = saveProduct(brand.getId(), "에어맥스");
            Product product2 = saveProduct(brand.getId(), "조던");
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            testRestTemplate.exchange(ENDPOINT + "/" + brand.getId(), HttpMethod.DELETE, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                () -> assertThat(productJpaRepository.findById(product1.getId()).orElseThrow().getDeletedAt()).isNotNull(),
                () -> assertThat(productJpaRepository.findById(product2.getId()).orElseThrow().getDeletedAt()).isNotNull()
            );
        }

        @DisplayName("브랜드를 삭제하면, 소속 상품의 좋아요도 모두 hard delete 처리된다.")
        @Test
        void hardDeletesAllLikes_whenBrandIsDeleted() {
            // arrange
            Brand brand = saveBrand("나이키", "스포츠 브랜드");
            Product product = saveProduct(brand.getId(), "에어맥스");
            User user = userJpaRepository.save(UserFixture.builder().loginId("brandDeleteUser").build());
            likeJpaRepository.save(Like.create(user.getId(), product.getId()));
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            testRestTemplate.exchange(ENDPOINT + "/" + brand.getId(), HttpMethod.DELETE, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertThat(likeJpaRepository.findByUserIdAndProductId(user.getId(), product.getId())).isEmpty();
        }
    }

    @DisplayName("브랜드 목록 조회 시")
    @Nested
    class GetBrands {
        @DisplayName("등록된 브랜드 목록을 페이지 단위로 반환한다.")
        @Test
        void returnsPagedBrands() {
            // arrange
            Brand nike = saveBrand("나이키", "스포츠");
            Brand adidas = saveBrand("아디다스", "독일 스포츠");
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<PageResponse<BrandV1Dto.AdminBrandResponse>>> response =
                    testRestTemplate.exchange(ENDPOINT + "?page=0&size=20", HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            // assert
            List<Long> ids = response.getBody()
                                     .data()
                                     .content()
                                     .stream()
                                     .map(BrandV1Dto.AdminBrandResponse::id)
                                     .toList();
            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(ids).contains(nike.getId(), adidas.getId())
            );
        }

        @DisplayName("삭제된 브랜드는 목록에서 제외된다.")
        @Test
        void excludesDeletedBrands() {
            // arrange
            Brand nike = saveBrand("나이키", "스포츠");
            Brand toDelete = saveBrand("삭제브랜드", "삭제될 브랜드");
            toDelete.delete();
            brandJpaRepository.save(toDelete);
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<PageResponse<BrandV1Dto.AdminBrandResponse>>> response =
                    testRestTemplate.exchange(ENDPOINT + "?page=0&size=20", HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            // assert
            List<Long> ids = response.getBody()
                                     .data()
                                     .content()
                                     .stream()
                                     .map(BrandV1Dto.AdminBrandResponse::id)
                                     .toList();

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(ids).contains(nike.getId()),
                () -> assertThat(ids).doesNotContain(toDelete.getId())
            );
        }
    }
}

package com.loopers.interfaces.api;

import com.loopers.domain.brand.model.Brand;
import com.loopers.domain.brand.model.BrandCommand;
import com.loopers.domain.member.model.Member;
import com.loopers.domain.member.model.MemberCommand;
import com.loopers.domain.member.service.PasswordEncryptor;
import com.loopers.domain.product.model.Product;
import com.loopers.domain.product.model.ProductCommand;
import com.loopers.infrastructure.brand.entity.BrandEntity;
import com.loopers.infrastructure.brand.repository.BrandJpaRepository;
import com.loopers.infrastructure.member.entity.MemberEntity;
import com.loopers.infrastructure.member.repository.MemberJpaRepository;
import com.loopers.infrastructure.product.entity.ProductEntity;
import com.loopers.infrastructure.product.repository.ProductJpaRepository;
import com.loopers.interfaces.api.product.dto.FindProductApiResDto;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private static final String ENDPOINT_PRODUCTS = "/api/v1/products";

    private final TestRestTemplate testRestTemplate;
    private final MemberJpaRepository memberJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final PasswordEncryptor passwordEncryptor;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public ProductV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        MemberJpaRepository memberJpaRepository,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        PasswordEncryptor passwordEncryptor,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.memberJpaRepository = memberJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.passwordEncryptor = passwordEncryptor;
        this.databaseCleanUp = databaseCleanUp;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders createAuthHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }

    private MemberEntity saveMember(String loginId, String rawPassword, String name, LocalDate birthDate, String email) {
        MemberCommand.SignUp command = new MemberCommand.SignUp(loginId, rawPassword, name, birthDate, email);
        Member model = Member.signUp(command, passwordEncryptor);
        return memberJpaRepository.save(MemberEntity.toEntity(model));
    }

    private BrandEntity saveBrand(String name, String description) {
        Brand brand = Brand.create(new BrandCommand.Create(name, description));
        return brandJpaRepository.save(BrandEntity.toEntity(brand));
    }

    private ProductEntity saveProduct(Long brandId, String name, int price, int stock) {
        Product product = Product.create(brandId, new ProductCommand.Create(brandId, name, price, stock));
        return productJpaRepository.save(ProductEntity.toEntity(product));
    }

    @DisplayName("GET /api/v1/products - 상품 목록 조회")
    @Nested
    class FindProductList {

        @DisplayName("상품이 없으면 빈 페이지를 반환한다")
        @Test
        void findProductList_empty() {
            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "?sortFilter=LATEST&page=0&size=10",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat((List<?>) response.getBody().data().get("content")).isEmpty()
            );
        }

        @DisplayName("상품 여러 건이 있으면 페이지 content에 목록을 반환한다")
        @Test
        void findProductList_multipleProducts() {
            // arrange
            BrandEntity brand = saveBrand("나이키", "스포츠 브랜드");
            saveProduct(brand.getId(), "에어맥스", 120000, 10);
            saveProduct(brand.getId(), "조던1", 200000, 5);

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "?sortFilter=LATEST&page=0&size=10",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat((List<?>) response.getBody().data().get("content")).hasSize(2)
            );
        }

        @DisplayName("brandId 필터를 사용하면 해당 브랜드 상품만 조회된다")
        @Test
        void findProductList_filterByBrandId() {
            // arrange
            BrandEntity brandA = saveBrand("나이키", "스포츠 브랜드");
            BrandEntity brandB = saveBrand("아디다스", "스포츠 브랜드");
            saveProduct(brandA.getId(), "에어맥스", 120000, 10);
            saveProduct(brandA.getId(), "조던1", 200000, 5);
            saveProduct(brandB.getId(), "삼바", 90000, 8);

            // act
            ParameterizedTypeReference<ApiResponse<Map<String, Object>>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<Map<String, Object>>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "?sortFilter=LATEST&page=0&size=10&brandId=" + brandA.getId(),
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat((List<?>) response.getBody().data().get("content")).hasSize(2)
            );
        }
    }

    @DisplayName("GET /api/v1/products/{productId} - 상품 상세 조회")
    @Nested
    class FindProduct {

        @DisplayName("존재하지 않는 상품을 조회하면 404 NOT_FOUND 응답을 받는다")
        @Test
        void findProduct_notFound() {
            // act
            ParameterizedTypeReference<ApiResponse<FindProductApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindProductApiResDto>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "/99999",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is4xxClientError()),
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
            );
        }

        @DisplayName("비로그인 상태로 상품 상세 조회 시 좋아요 여부는 false이다")
        @Test
        void findProduct_notLoggedIn_isFavoriteFalse() {
            // arrange
            BrandEntity brand = saveBrand("나이키", "스포츠 브랜드");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 120000, 10);

            // act
            ParameterizedTypeReference<ApiResponse<FindProductApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindProductApiResDto>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "/" + product.getId(),
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().isFavorite()).isFalse()
            );
        }

        @DisplayName("로그인 상태로 상품 상세 조회 시 브랜드명, 좋아요수, 좋아요여부를 반환한다")
        @Test
        void findProduct_loggedIn_returnsBrandAndFavoriteInfo() {
            // arrange
            String password = "Pass1234!";
            saveMember("testuser", password, "홍길동", LocalDate.of(1990, 1, 15), "test@example.com");
            BrandEntity brand = saveBrand("나이키", "스포츠 브랜드");
            ProductEntity product = saveProduct(brand.getId(), "에어맥스", 120000, 10);

            HttpHeaders headers = createAuthHeaders("testuser", password);

            // act
            ParameterizedTypeReference<ApiResponse<FindProductApiResDto>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<FindProductApiResDto>> response =
                testRestTemplate.exchange(
                    ENDPOINT_PRODUCTS + "/" + product.getId(),
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    responseType
                );

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().brandName()).isEqualTo("나이키"),
                () -> assertThat(response.getBody().data().favoriteCnt()).isZero(),
                () -> assertThat(response.getBody().data().isFavorite()).isFalse()
            );
        }
    }
}

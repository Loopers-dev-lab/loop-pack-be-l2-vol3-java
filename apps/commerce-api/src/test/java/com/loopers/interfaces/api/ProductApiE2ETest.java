package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.PasswordEncoder;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.OptionRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.product.ProductDto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProductApiE2ETest {

    private static final String PRODUCTS_ENDPOINT = "/api/v1/products";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private BrandRepository brandRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OptionRepository optionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Brand testBrand;
    private Product testProduct;
    private Option testOption;

    @BeforeEach
    void setUp() {
        testBrand = brandRepository.save(Brand.create("테스트 브랜드"));
        testProduct = productRepository.save(Product.create(testBrand.getId(), "테스트 상품", Money.of(BigDecimal.valueOf(10000))));
        testOption = optionRepository.save(Option.create(testProduct.getId(), "기본 옵션", Money.zero(), 100));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("GET /api/v1/products")
    @Nested
    class GetProductList {

        @DisplayName("비로그인 상태에서 상품 목록을 조회할 수 있다.")
        @Test
        void getProductList_success_without_login() {
            // act
            ResponseEntity<ApiResponse<ProductDto.ProductListResponse>> response = testRestTemplate.exchange(
                    PRODUCTS_ENDPOINT,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().products()).hasSize(1),
                    () -> assertThat(response.getBody().data().products().get(0).productName()).isEqualTo("테스트 상품"),
                    () -> assertThat(response.getBody().data().products().get(0).likedByUser()).isFalse()
            );
        }

        @DisplayName("로그인 상태에서 상품 목록 조회 시 좋아요 여부가 표시된다.")
        @Test
        void getProductList_success_with_login() {
            // arrange
            Member member = createTestMember("testuser", "Password1!");

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-Loopers-LoginId", "testuser");
            headers.set("X-Loopers-LoginPw", "Password1!");
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            // act
            ResponseEntity<ApiResponse<ProductDto.ProductListResponse>> response = testRestTemplate.exchange(
                    PRODUCTS_ENDPOINT,
                    HttpMethod.GET,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().products()).hasSize(1),
                    () -> assertThat(response.getBody().data().products().get(0).likedByUser()).isFalse()
            );
        }

        @DisplayName("정렬 조건을 지정하여 상품 목록을 조회할 수 있다.")
        @Test
        void getProductList_with_sort_condition() {
            // arrange
            Product cheapProduct = productRepository.save(Product.create(testBrand.getId(), "저렴한 상품", Money.of(BigDecimal.valueOf(5000))));
            optionRepository.save(Option.create(cheapProduct.getId(), "기본", Money.zero(), 50));

            // act
            ResponseEntity<ApiResponse<ProductDto.ProductListResponse>> response = testRestTemplate.exchange(
                    PRODUCTS_ENDPOINT + "?sort=PRICE_ASC",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().products()).hasSize(2),
                    () -> assertThat(response.getBody().data().products().get(0).basePrice())
                            .isEqualByComparingTo(BigDecimal.valueOf(5000))
            );
        }
    }

    @DisplayName("GET /api/v1/products/{productId}")
    @Nested
    class GetProductDetail {

        @DisplayName("비로그인 상태에서 상품 상세를 조회할 수 있다.")
        @Test
        void getProductDetail_success_without_login() {
            // act
            ResponseEntity<ApiResponse<ProductDto.ProductResponse>> response = testRestTemplate.exchange(
                    PRODUCTS_ENDPOINT + "/" + testProduct.getId(),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().productId()).isEqualTo(testProduct.getId()),
                    () -> assertThat(response.getBody().data().productName()).isEqualTo("테스트 상품"),
                    () -> assertThat(response.getBody().data().brandName()).isEqualTo("테스트 브랜드"),
                    () -> assertThat(response.getBody().data().options()).hasSize(1),
                    () -> assertThat(response.getBody().data().options().get(0).optionName()).isEqualTo("기본 옵션")
            );
        }

        @DisplayName("존재하지 않는 상품을 조회하면 404 응답을 받는다.")
        @Test
        void getProductDetail_fail_not_found() {
            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    PRODUCTS_ENDPOINT + "/999999",
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL)
            );
        }
    }

    private Member createTestMember(String memberId, String rawPassword) {
        String encodedPassword = passwordEncoder.encode(rawPassword);
        Member member = Member.create(
                new MemberId(memberId),
                Password.ofEncoded(encodedPassword),
                new Name("테스트"),
                new Email(memberId + "@test.com"),
                new BirthDate("1997-01-01")
        );
        return memberRepository.save(member);
    }
}

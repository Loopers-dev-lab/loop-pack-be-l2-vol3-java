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
import com.loopers.interfaces.api.like.LikeDto;
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
class LikeApiE2ETest {

    private static final String LIKES_ENDPOINT = "/api/v1/likes";

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
    private Member testMember;
    private static final String TEST_PASSWORD = "Password1!";

    @BeforeEach
    void setUp() {
        testBrand = brandRepository.save(Brand.create("테스트 브랜드"));
        testProduct = productRepository.save(Product.create(testBrand.getId(), "테스트 상품", Money.of(BigDecimal.valueOf(10000))));
        optionRepository.save(Option.create(testProduct.getId(), "기본 옵션", Money.zero(), 100));
        testMember = createTestMember("testuser", TEST_PASSWORD);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/likes/{productId}")
    @Nested
    class ToggleLike {

        @DisplayName("좋아요를 누르면 liked=true 응답을 받는다.")
        @Test
        void toggleLike_add_success() {
            // arrange
            HttpHeaders headers = createAuthHeaders("testuser", TEST_PASSWORD);
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            // act
            ResponseEntity<ApiResponse<LikeDto.ToggleResponse>> response = testRestTemplate.exchange(
                    LIKES_ENDPOINT + "/" + testProduct.getId(),
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().liked()).isTrue()
            );
        }

        @DisplayName("이미 좋아요한 상품에 다시 좋아요를 누르면 취소된다 (liked=false).")
        @Test
        void toggleLike_remove_success() {
            // arrange
            HttpHeaders headers = createAuthHeaders("testuser", TEST_PASSWORD);
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            // 먼저 좋아요
            testRestTemplate.exchange(
                    LIKES_ENDPOINT + "/" + testProduct.getId(),
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<ApiResponse<LikeDto.ToggleResponse>>() {}
            );

            // act - 다시 좋아요 (토글)
            ResponseEntity<ApiResponse<LikeDto.ToggleResponse>> response = testRestTemplate.exchange(
                    LIKES_ENDPOINT + "/" + testProduct.getId(),
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().liked()).isFalse()
            );
        }

        @DisplayName("비로그인 상태에서 좋아요를 누르면 401 응답을 받는다.")
        @Test
        void toggleLike_fail_unauthorized() {
            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKES_ENDPOINT + "/" + testProduct.getId(),
                    HttpMethod.POST,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 상품에 좋아요를 누르면 404 응답을 받는다.")
        @Test
        void toggleLike_fail_product_not_found() {
            // arrange
            HttpHeaders headers = createAuthHeaders("testuser", TEST_PASSWORD);
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKES_ENDPOINT + "/999999",
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/likes")
    @Nested
    class GetLikedProducts {

        @DisplayName("좋아요한 상품 목록을 조회할 수 있다.")
        @Test
        void getLikedProducts_success() {
            // arrange
            HttpHeaders headers = createAuthHeaders("testuser", TEST_PASSWORD);
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            // 먼저 좋아요
            testRestTemplate.exchange(
                    LIKES_ENDPOINT + "/" + testProduct.getId(),
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<ApiResponse<LikeDto.ToggleResponse>>() {}
            );

            // act
            ResponseEntity<ApiResponse<LikeDto.LikeListResponse>> response = testRestTemplate.exchange(
                    LIKES_ENDPOINT,
                    HttpMethod.GET,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().likes()).hasSize(1),
                    () -> assertThat(response.getBody().data().likes().get(0).productName()).isEqualTo("테스트 상품")
            );
        }

        @DisplayName("좋아요한 상품이 없으면 빈 목록을 반환한다.")
        @Test
        void getLikedProducts_empty() {
            // arrange
            HttpHeaders headers = createAuthHeaders("testuser", TEST_PASSWORD);
            HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

            // act
            ResponseEntity<ApiResponse<LikeDto.LikeListResponse>> response = testRestTemplate.exchange(
                    LIKES_ENDPOINT,
                    HttpMethod.GET,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().likes()).isEmpty()
            );
        }

        @DisplayName("비로그인 상태에서 좋아요 목록을 조회하면 401 응답을 받는다.")
        @Test
        void getLikedProducts_fail_unauthorized() {
            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    LIKES_ENDPOINT,
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
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

    private HttpHeaders createAuthHeaders(String memberId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", memberId);
        headers.set("X-Loopers-LoginPw", password);
        return headers;
    }
}

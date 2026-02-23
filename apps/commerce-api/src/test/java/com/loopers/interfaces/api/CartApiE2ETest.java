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
import com.loopers.interfaces.api.cart.CartDto;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartApiE2ETest {

    private static final String CART_ENDPOINT = "/api/v1/cart";

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
    private Member testMember;
    private static final String TEST_PASSWORD = "Password1!";

    @BeforeEach
    void setUp() {
        testBrand = brandRepository.save(Brand.create("테스트 브랜드"));
        testProduct = productRepository.save(Product.create(testBrand.getId(), "테스트 상품", Money.of(BigDecimal.valueOf(10000))));
        testOption = optionRepository.save(Option.create(testProduct.getId(), "기본 옵션", Money.of(BigDecimal.valueOf(1000)), 100));
        testMember = createTestMember("testuser", TEST_PASSWORD);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/cart")
    @Nested
    class AddToCart {

        @DisplayName("장바구니에 상품을 담을 수 있다.")
        @Test
        void addToCart_success() {
            // arrange
            CartDto.AddRequest request = new CartDto.AddRequest(testOption.getId(), 2);
            HttpEntity<CartDto.AddRequest> httpEntity = createHttpEntity(request, "testuser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<CartDto.AddResponse>> response = testRestTemplate.exchange(
                    CART_ENDPOINT,
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().optionId()).isEqualTo(testOption.getId()),
                    () -> assertThat(response.getBody().data().quantity()).isEqualTo(2)
            );
        }

        @DisplayName("동일 옵션을 장바구니에 다시 담으면 수량이 합산된다.")
        @Test
        void addToCart_merge_quantity() {
            // arrange
            CartDto.AddRequest request1 = new CartDto.AddRequest(testOption.getId(), 2);
            HttpEntity<CartDto.AddRequest> httpEntity1 = createHttpEntity(request1, "testuser", TEST_PASSWORD);
            testRestTemplate.exchange(CART_ENDPOINT, HttpMethod.POST, httpEntity1, new ParameterizedTypeReference<ApiResponse<CartDto.AddResponse>>() {});

            CartDto.AddRequest request2 = new CartDto.AddRequest(testOption.getId(), 3);
            HttpEntity<CartDto.AddRequest> httpEntity2 = createHttpEntity(request2, "testuser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<CartDto.AddResponse>> response = testRestTemplate.exchange(
                    CART_ENDPOINT,
                    HttpMethod.POST,
                    httpEntity2,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().quantity()).isEqualTo(5)
            );
        }

        @DisplayName("비로그인 상태에서 장바구니에 담으면 401 응답을 받는다.")
        @Test
        void addToCart_fail_unauthorized() {
            // arrange
            CartDto.AddRequest request = new CartDto.AddRequest(testOption.getId(), 2);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<CartDto.AddRequest> httpEntity = new HttpEntity<>(request, headers);

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    CART_ENDPOINT,
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @DisplayName("존재하지 않는 옵션을 장바구니에 담으면 404 응답을 받는다.")
        @Test
        void addToCart_fail_option_not_found() {
            // arrange
            CartDto.AddRequest request = new CartDto.AddRequest(999999L, 2);
            HttpEntity<CartDto.AddRequest> httpEntity = createHttpEntity(request, "testuser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    CART_ENDPOINT,
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("GET /api/v1/cart")
    @Nested
    class GetCart {

        @DisplayName("장바구니를 조회할 수 있다.")
        @Test
        void getCart_success() {
            // arrange - 먼저 장바구니에 담기
            CartDto.AddRequest addRequest = new CartDto.AddRequest(testOption.getId(), 2);
            HttpEntity<CartDto.AddRequest> addEntity = createHttpEntity(addRequest, "testuser", TEST_PASSWORD);
            testRestTemplate.exchange(CART_ENDPOINT, HttpMethod.POST, addEntity, new ParameterizedTypeReference<ApiResponse<CartDto.AddResponse>>() {});

            HttpEntity<Void> httpEntity = createHttpEntity(null, "testuser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<CartDto.CartResponse>> response = testRestTemplate.exchange(
                    CART_ENDPOINT,
                    HttpMethod.GET,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().items()).hasSize(1),
                    () -> assertThat(response.getBody().data().items().get(0).productName()).isEqualTo("테스트 상품"),
                    () -> assertThat(response.getBody().data().items().get(0).optionName()).isEqualTo("기본 옵션"),
                    () -> assertThat(response.getBody().data().items().get(0).quantity()).isEqualTo(2),
                    () -> assertThat(response.getBody().data().items().get(0).unitPrice()).isEqualByComparingTo(BigDecimal.valueOf(11000)),
                    () -> assertThat(response.getBody().data().items().get(0).orderable()).isTrue()
            );
        }

        @DisplayName("빈 장바구니를 조회하면 빈 목록을 반환한다.")
        @Test
        void getCart_empty() {
            // arrange
            HttpEntity<Void> httpEntity = createHttpEntity(null, "testuser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<CartDto.CartResponse>> response = testRestTemplate.exchange(
                    CART_ENDPOINT,
                    HttpMethod.GET,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().items()).isEmpty()
            );
        }
    }

    @DisplayName("PATCH /api/v1/cart/{itemId}")
    @Nested
    class UpdateQuantity {

        @DisplayName("장바구니 항목의 수량을 변경할 수 있다.")
        @Test
        void updateQuantity_success() {
            // arrange - 먼저 장바구니에 담기
            CartDto.AddRequest addRequest = new CartDto.AddRequest(testOption.getId(), 2);
            HttpEntity<CartDto.AddRequest> addEntity = createHttpEntity(addRequest, "testuser", TEST_PASSWORD);
            ResponseEntity<ApiResponse<CartDto.AddResponse>> addResponse = testRestTemplate.exchange(
                    CART_ENDPOINT, HttpMethod.POST, addEntity, new ParameterizedTypeReference<>() {}
            );
            Long cartItemId = addResponse.getBody().data().cartItemId();

            CartDto.UpdateQuantityRequest updateRequest = new CartDto.UpdateQuantityRequest(5);
            HttpEntity<CartDto.UpdateQuantityRequest> updateEntity = createHttpEntity(updateRequest, "testuser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<CartDto.AddResponse>> response = testRestTemplate.exchange(
                    CART_ENDPOINT + "/" + cartItemId,
                    HttpMethod.PATCH,
                    updateEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().quantity()).isEqualTo(5)
            );
        }
    }

    @DisplayName("DELETE /api/v1/cart/{itemId}")
    @Nested
    class DeleteCartItem {

        @DisplayName("장바구니 항목을 삭제할 수 있다.")
        @Test
        void delete_success() {
            // arrange - 먼저 장바구니에 담기
            CartDto.AddRequest addRequest = new CartDto.AddRequest(testOption.getId(), 2);
            HttpEntity<CartDto.AddRequest> addEntity = createHttpEntity(addRequest, "testuser", TEST_PASSWORD);
            ResponseEntity<ApiResponse<CartDto.AddResponse>> addResponse = testRestTemplate.exchange(
                    CART_ENDPOINT, HttpMethod.POST, addEntity, new ParameterizedTypeReference<>() {}
            );
            Long cartItemId = addResponse.getBody().data().cartItemId();

            HttpEntity<Void> deleteEntity = createHttpEntity(null, "testuser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    CART_ENDPOINT + "/" + cartItemId,
                    HttpMethod.DELETE,
                    deleteEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            // 장바구니 조회하면 빈 목록
            HttpEntity<Void> getEntity = createHttpEntity(null, "testuser", TEST_PASSWORD);
            ResponseEntity<ApiResponse<CartDto.CartResponse>> cartResponse = testRestTemplate.exchange(
                    CART_ENDPOINT, HttpMethod.GET, getEntity, new ParameterizedTypeReference<>() {}
            );
            assertThat(cartResponse.getBody().data().items()).isEmpty();
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

    private <T> HttpEntity<T> createHttpEntity(T body, String memberId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", memberId);
        headers.set("X-Loopers-LoginPw", password);
        return new HttpEntity<>(body, headers);
    }
}

package com.loopers.interfaces.api.cart;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.cart.CartItem;
import com.loopers.domain.cart.CartItemRepository;
import com.loopers.domain.inventory.Inventory;
import com.loopers.domain.inventory.InventoryRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductStatus;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserRequest;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class CartItemApiE2ETest {

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @BeforeEach
    void setUp() {
        UserRequest.SignupRequest signupRequest = new UserRequest.SignupRequest(
                "testuser", "Hx7!mK2@", "테스터", "1994-11-15", "test@example.com");
        testRestTemplate.postForEntity("/api/v1/users", signupRequest, ApiResponse.class);
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "testuser");
        headers.set("X-Loopers-LoginPw", "Hx7!mK2@");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private Brand createActiveBrand(String name) {
        return brandRepository.save(Brand.register(name, name + " 설명"));
    }

    private Product createProduct(Long brandId, String name, ProductStatus status) {
        Product product = Product.register(brandId, name, name + " 설명", 10000);
        if (status != ProductStatus.ACTIVE) {
            product.changeStatus(status);
        }
        Product saved = productRepository.save(product);
        inventoryRepository.save(Inventory.initialize(saved.getId(), 100));
        return saved;
    }

    private Product createActiveProduct(Long brandId, String name) {
        return createProduct(brandId, name, ProductStatus.ACTIVE);
    }

    @DisplayName("GET /api/v1/carts")
    @Nested
    class 장바구니_조회 {

        @Test
        void 장바구니_조회에_성공하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");
            testRestTemplate.exchange("/api/v1/carts/items", HttpMethod.POST,
                    new HttpEntity<>(new CartItemRequest.AddCartItemRequest(product.getId(), 2), authHeaders()),
                    ApiResponse.class);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts", HttpMethod.GET, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 빈_장바구니는_빈_목록을_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts", HttpMethod.GET, new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 인증_없이_요청하면_401_Unauthorized를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts", HttpMethod.GET, new HttpEntity<>(new HttpHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("POST /api/v1/carts/items")
    @Nested
    class 장바구니_추가 {

        @Test
        void 추가에_성공하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts/items", HttpMethod.POST,
                    new HttpEntity<>(new CartItemRequest.AddCartItemRequest(product.getId(), 2), authHeaders()),
                    ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 동일_상품을_추가하면_수량이_합산된다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");

            testRestTemplate.exchange("/api/v1/carts/items", HttpMethod.POST,
                    new HttpEntity<>(new CartItemRequest.AddCartItemRequest(product.getId(), 2), authHeaders()),
                    ApiResponse.class);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts/items", HttpMethod.POST,
                    new HttpEntity<>(new CartItemRequest.AddCartItemRequest(product.getId(), 3), authHeaders()),
                    ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 존재하지_않는_상품이면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts/items", HttpMethod.POST,
                    new HttpEntity<>(new CartItemRequest.AddCartItemRequest(999L, 1), authHeaders()),
                    ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 판매_불가능한_상품이면_409_Conflict를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createProduct(brand.getId(), "품절상품", ProductStatus.SOLDOUT);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts/items", HttpMethod.POST,
                    new HttpEntity<>(new CartItemRequest.AddCartItemRequest(product.getId(), 1), authHeaders()),
                    ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }

        @Test
        void 인증_없이_요청하면_401_Unauthorized를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");

            // act
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts/items", HttpMethod.POST,
                    new HttpEntity<>(new CartItemRequest.AddCartItemRequest(product.getId(), 1), headers),
                    ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PUT /api/v1/carts/items/{cartItemId}")
    @Nested
    class 수량_변경 {

        @Test
        void 수량_변경에_성공하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");
            testRestTemplate.exchange("/api/v1/carts/items", HttpMethod.POST,
                    new HttpEntity<>(new CartItemRequest.AddCartItemRequest(product.getId(), 2), authHeaders()),
                    ApiResponse.class);
            CartItem cartItem = cartItemRepository.findAllByUserId(1L).get(0);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts/items/" + cartItem.getId(), HttpMethod.PUT,
                    new HttpEntity<>(new CartItemRequest.ChangeQuantityRequest(5), authHeaders()),
                    ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 수량이_0_이하이면_400_Bad_Request를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");
            testRestTemplate.exchange("/api/v1/carts/items", HttpMethod.POST,
                    new HttpEntity<>(new CartItemRequest.AddCartItemRequest(product.getId(), 2), authHeaders()),
                    ApiResponse.class);
            CartItem cartItem = cartItemRepository.findAllByUserId(1L).get(0);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts/items/" + cartItem.getId(), HttpMethod.PUT,
                    new HttpEntity<>(new CartItemRequest.ChangeQuantityRequest(0), authHeaders()),
                    ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 존재하지_않는_항목이면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts/items/999", HttpMethod.PUT,
                    new HttpEntity<>(new CartItemRequest.ChangeQuantityRequest(5), authHeaders()),
                    ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 타인의_장바구니_항목이면_403_Forbidden을_반환한다() {
            // arrange - 다른 사용자의 장바구니 항목 직접 생성
            CartItem otherUserItem = cartItemRepository.save(CartItem.of(999L, 100L, 3));

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts/items/" + otherUserItem.getId(), HttpMethod.PUT,
                    new HttpEntity<>(new CartItemRequest.ChangeQuantityRequest(5), authHeaders()),
                    ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }
    }

    @DisplayName("DELETE /api/v1/carts/items/{cartItemId}")
    @Nested
    class 장바구니_삭제 {

        @Test
        void 삭제에_성공하면_200_OK를_반환한다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");
            testRestTemplate.exchange("/api/v1/carts/items", HttpMethod.POST,
                    new HttpEntity<>(new CartItemRequest.AddCartItemRequest(product.getId(), 2), authHeaders()),
                    ApiResponse.class);
            CartItem cartItem = cartItemRepository.findAllByUserId(1L).get(0);

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts/items/" + cartItem.getId(), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void 존재하지_않는_항목이면_404_Not_Found를_반환한다() {
            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts/items/999", HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @Test
        void 타인의_장바구니_항목이면_403_Forbidden을_반환한다() {
            // arrange
            CartItem otherUserItem = cartItemRepository.save(CartItem.of(999L, 100L, 3));

            // act
            ResponseEntity<ApiResponse> response = testRestTemplate.exchange(
                    "/api/v1/carts/items/" + otherUserItem.getId(), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @Test
        void 삭제_후_장바구니에서_조회되지_않는다() {
            // arrange
            Brand brand = createActiveBrand("나이키");
            Product product = createActiveProduct(brand.getId(), "에어맥스");
            testRestTemplate.exchange("/api/v1/carts/items", HttpMethod.POST,
                    new HttpEntity<>(new CartItemRequest.AddCartItemRequest(product.getId(), 2), authHeaders()),
                    ApiResponse.class);
            CartItem cartItem = cartItemRepository.findAllByUserId(1L).get(0);

            // act
            testRestTemplate.exchange(
                    "/api/v1/carts/items/" + cartItem.getId(), HttpMethod.DELETE,
                    new HttpEntity<>(authHeaders()), ApiResponse.class);

            // assert
            assertThat(cartItemRepository.findById(cartItem.getId())).isEmpty();
        }
    }
}

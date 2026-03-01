package com.loopers.interfaces.api;

import com.loopers.interfaces.api.brand.AdminBrandV1Dto;
import com.loopers.interfaces.api.cart.CartV1Dto;
import com.loopers.interfaces.api.product.AdminProductV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CartV1ApiE2ETest {

    private static final String CART_ENDPOINT = "/api/v1/cart";
    private static final String CART_ITEMS_ENDPOINT = "/api/v1/cart/items";
    private static final String ADMIN_BRAND_ENDPOINT = "/api-admin/v1/brands";
    private static final String ADMIN_PRODUCT_ENDPOINT = "/api-admin/v1/products";
    private static final String SIGNUP_ENDPOINT = "/api/v1/users";

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;

    @Autowired
    public CartV1ApiE2ETest(TestRestTemplate testRestTemplate, DatabaseCleanUp databaseCleanUp) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
    }

    private Long productId;
    private Long productId2;

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "loopers.admin");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "testUser1");
        headers.set("X-Loopers-LoginPw", "Abcd1234!");
        headers.set("Content-Type", "application/json");
        return headers;
    }

    private void signupUser() {
        UserV1Dto.SignupRequest request = new UserV1Dto.SignupRequest(
            "testUser1", "Abcd1234!", "홍길동", LocalDate.of(1995, 3, 15), "test@example.com"
        );
        testRestTemplate.exchange(SIGNUP_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {});
    }

    @BeforeEach
    void setUp() {
        signupUser();

        AdminBrandV1Dto.CreateRequest brandRequest = new AdminBrandV1Dto.CreateRequest("나이키");
        ResponseEntity<ApiResponse<AdminBrandV1Dto.BrandResponse>> brandResponse = testRestTemplate.exchange(
            ADMIN_BRAND_ENDPOINT, HttpMethod.POST, new HttpEntity<>(brandRequest, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        Long brandId = brandResponse.getBody().data().id();

        AdminProductV1Dto.CreateRequest productRequest1 = new AdminProductV1Dto.CreateRequest(brandId, "에어맥스", 129000, 100);
        ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> productResponse1 = testRestTemplate.exchange(
            ADMIN_PRODUCT_ENDPOINT, HttpMethod.POST, new HttpEntity<>(productRequest1, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        productId = productResponse1.getBody().data().id();

        AdminProductV1Dto.CreateRequest productRequest2 = new AdminProductV1Dto.CreateRequest(brandId, "에어포스1", 109000, 200);
        ResponseEntity<ApiResponse<AdminProductV1Dto.ProductResponse>> productResponse2 = testRestTemplate.exchange(
            ADMIN_PRODUCT_ENDPOINT, HttpMethod.POST, new HttpEntity<>(productRequest2, adminHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        productId2 = productResponse2.getBody().data().id();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long addToCart(Long productId, int quantity) {
        CartV1Dto.AddRequest request = new CartV1Dto.AddRequest(productId, quantity);
        testRestTemplate.exchange(
            CART_ITEMS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
            new ParameterizedTypeReference<ApiResponse<Object>>() {}
        );

        ResponseEntity<ApiResponse<CartV1Dto.CartResponse>> cartResponse = testRestTemplate.exchange(
            CART_ENDPOINT, HttpMethod.GET, new HttpEntity<>(authHeaders()),
            new ParameterizedTypeReference<>() {}
        );
        return cartResponse.getBody().data().items().stream()
            .filter(item -> item.productId().equals(productId))
            .findFirst()
            .map(CartV1Dto.CartItemResponse::cartItemId)
            .orElse(null);
    }

    @DisplayName("POST /api/v1/cart/items")
    @Nested
    class AddToCart {

        @DisplayName("새 상품을 담으면, 200 OK를 반환한다.")
        @Test
        void returnsSuccess_whenAddingNewProduct() {
            CartV1Dto.AddRequest request = new CartV1Dto.AddRequest(productId, 2);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                CART_ITEMS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertTrue(response.getStatusCode().is2xxSuccessful());
        }

        @DisplayName("이미 담긴 상품을 다시 담으면, 수량이 합산된다.")
        @Test
        void addsQuantity_whenProductAlreadyInCart() {
            CartV1Dto.AddRequest request1 = new CartV1Dto.AddRequest(productId, 2);
            testRestTemplate.exchange(
                CART_ITEMS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request1, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );

            CartV1Dto.AddRequest request2 = new CartV1Dto.AddRequest(productId, 3);
            testRestTemplate.exchange(
                CART_ITEMS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request2, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<Object>>() {}
            );

            ResponseEntity<ApiResponse<CartV1Dto.CartResponse>> cartResponse = testRestTemplate.exchange(
                CART_ENDPOINT, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertThat(cartResponse.getBody().data().items()).hasSize(1),
                () -> assertThat(cartResponse.getBody().data().items().get(0).quantity()).isEqualTo(5)
            );
        }

        @DisplayName("존재하지 않는 상품이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenProductDoesNotExist() {
            CartV1Dto.AddRequest request = new CartV1Dto.AddRequest(999L, 2);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                CART_ITEMS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("인증되지 않은 사용자이면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenNotAuthenticated() {
            CartV1Dto.AddRequest request = new CartV1Dto.AddRequest(productId, 2);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                CART_ITEMS_ENDPOINT, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/cart")
    @Nested
    class GetMyCart {

        @DisplayName("장바구니에 항목이 있으면, 상품/브랜드 정보와 함께 목록을 반환한다.")
        @Test
        void returnsCartWithProductInfo_whenItemsExist() {
            addToCart(productId, 2);
            addToCart(productId2, 1);

            ResponseEntity<ApiResponse<CartV1Dto.CartResponse>> response = testRestTemplate.exchange(
                CART_ENDPOINT, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().items()).hasSize(2),
                () -> assertThat(response.getBody().data().items().get(0).brandName()).isEqualTo("나이키")
            );
        }

        @DisplayName("장바구니가 비어있으면, 빈 목록을 반환한다.")
        @Test
        void returnsEmptyList_whenCartIsEmpty() {
            ResponseEntity<ApiResponse<CartV1Dto.CartResponse>> response = testRestTemplate.exchange(
                CART_ENDPOINT, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody().data().items()).isEmpty()
            );
        }
    }

    @DisplayName("PUT /api/v1/cart/items/{cartItemId}")
    @Nested
    class UpdateQuantity {

        @DisplayName("올바른 수량이면, 200 OK를 반환한다.")
        @Test
        void returnsSuccess_whenValidQuantity() {
            Long cartItemId = addToCart(productId, 2);

            CartV1Dto.UpdateQuantityRequest request = new CartV1Dto.UpdateQuantityRequest(5);
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                CART_ITEMS_ENDPOINT + "/" + cartItemId, HttpMethod.PUT,
                new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertTrue(response.getStatusCode().is2xxSuccessful());

            ResponseEntity<ApiResponse<CartV1Dto.CartResponse>> cartResponse = testRestTemplate.exchange(
                CART_ENDPOINT, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(cartResponse.getBody().data().items().get(0).quantity()).isEqualTo(5);
        }

        @DisplayName("존재하지 않는 항목이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenItemDoesNotExist() {
            CartV1Dto.UpdateQuantityRequest request = new CartV1Dto.UpdateQuantityRequest(5);
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                CART_ITEMS_ENDPOINT + "/999", HttpMethod.PUT,
                new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @DisplayName("DELETE /api/v1/cart/items/{cartItemId}")
    @Nested
    class RemoveItem {

        @DisplayName("존재하는 항목이면, 200 OK를 반환하고 삭제된다.")
        @Test
        void returnsSuccess_whenItemExists() {
            Long cartItemId = addToCart(productId, 2);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                CART_ITEMS_ENDPOINT + "/" + cartItemId, HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertTrue(response.getStatusCode().is2xxSuccessful());

            ResponseEntity<ApiResponse<CartV1Dto.CartResponse>> cartResponse = testRestTemplate.exchange(
                CART_ENDPOINT, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );
            assertThat(cartResponse.getBody().data().items()).isEmpty();
        }

        @DisplayName("존재하지 않는 항목이면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenItemDoesNotExist() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                CART_ITEMS_ENDPOINT + "/999", HttpMethod.DELETE,
                new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}

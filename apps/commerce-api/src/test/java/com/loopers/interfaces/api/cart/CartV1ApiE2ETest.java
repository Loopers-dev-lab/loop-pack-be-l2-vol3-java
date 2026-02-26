package com.loopers.interfaces.api.cart;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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

import java.math.BigDecimal;
import java.util.List;

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MySqlTestContainersConfig.class)
class CartV1ApiE2ETest {

    private static final String ENDPOINT_CART_ITEMS = "/api/v1/cart/items";
    private static final String LOGIN_ID = "cartuser";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;
    @Autowired
    private BrandService brandService;
    @Autowired
    private ProductService productService;

    private Long productId;

    @BeforeEach
    void setUp() {
        UserV1Dto.SignUpRequest signUp = new UserV1Dto.SignUpRequest(
            "cartuser", "SecurePass1!", "cart@example.com", "1990-01-15", "MALE");
        testRestTemplate.exchange("/api/v1/users", HttpMethod.POST, new HttpEntity<>(signUp),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});
        BrandModel brand = brandService.register("E2E브랜드");
        ProductModel product = productService.register(brand.getId(), "E2E상품", new BigDecimal("5000"), 10);
        productId = product.getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.set("X-Loopers-LoginId", LOGIN_ID);
        return h;
    }

    @DisplayName("POST /api/v1/cart/items - 장바구니 담기")
    @Nested
    class AddItem {

        @Test
        void addItem_withValidRequest_shouldReturn201() {
            CartV1Dto.AddItemRequest request = new CartV1Dto.AddItemRequest(productId, null, 2);

            ResponseEntity<ApiResponse<CartV1Dto.CartItemResponse>> response = testRestTemplate.exchange(
                ENDPOINT_CART_ITEMS, HttpMethod.POST, new HttpEntity<>(request, authHeaders()),
                new ParameterizedTypeReference<>() {});

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(Result.SUCCESS),
                () -> assertThat(response.getBody().data().productId()).isEqualTo(productId),
                () -> assertThat(response.getBody().data().quantity()).isEqualTo(2)
            );
        }

        @Test
        void addItem_withoutLogin_shouldReturn401() {
            CartV1Dto.AddItemRequest request = new CartV1Dto.AddItemRequest(productId, null, 1);

            ResponseEntity<ApiResponse<CartV1Dto.CartItemResponse>> response = testRestTemplate.exchange(
                ENDPOINT_CART_ITEMS, HttpMethod.POST, new HttpEntity<>(request), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("GET /api/v1/cart/items - 장바구니 조회")
    @Nested
    class GetItems {

        @Test
        void getItems_withValidRequest_shouldReturn200() {
            CartV1Dto.AddItemRequest addReq = new CartV1Dto.AddItemRequest(productId, null, 1);
            testRestTemplate.exchange(ENDPOINT_CART_ITEMS, HttpMethod.POST, new HttpEntity<>(addReq, authHeaders()),
                new ParameterizedTypeReference<ApiResponse<CartV1Dto.CartItemResponse>>() {});

            ResponseEntity<ApiResponse<List<CartV1Dto.CartItemResponse>>> response = testRestTemplate.exchange(
                ENDPOINT_CART_ITEMS, HttpMethod.GET, new HttpEntity<>(authHeaders()),
                new ParameterizedTypeReference<>() {});

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data()).hasSize(1),
                () -> assertThat(response.getBody().data().get(0).productId()).isEqualTo(productId)
            );
        }

        @Test
        void getItems_withoutLogin_shouldReturn401() {
            ResponseEntity<ApiResponse<List<CartV1Dto.CartItemResponse>>> response = testRestTemplate.exchange(
                ENDPOINT_CART_ITEMS, HttpMethod.GET, new HttpEntity<>(null), new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @DisplayName("PUT /api/v1/cart/items/{cartItemId} - 수량 수정")
    @Nested
    class UpdateItem {

        @Test
        void updateItem_withValidRequest_shouldReturn200() {
            CartV1Dto.AddItemRequest addReq = new CartV1Dto.AddItemRequest(productId, null, 1);
            ResponseEntity<ApiResponse<CartV1Dto.CartItemResponse>> addRes = testRestTemplate.exchange(
                ENDPOINT_CART_ITEMS, HttpMethod.POST, new HttpEntity<>(addReq, authHeaders()),
                new ParameterizedTypeReference<>() {});
            Long cartItemId = addRes.getBody().data().id();

            CartV1Dto.UpdateItemRequest updateReq = new CartV1Dto.UpdateItemRequest(3, null);
            ResponseEntity<ApiResponse<CartV1Dto.CartItemResponse>> response = testRestTemplate.exchange(
                ENDPOINT_CART_ITEMS + "/" + cartItemId, HttpMethod.PUT, new HttpEntity<>(updateReq, authHeaders()),
                new ParameterizedTypeReference<>() {});

            assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getBody().data().quantity()).isEqualTo(3)
            );
        }
    }

    @DisplayName("DELETE /api/v1/cart/items - 품목 삭제")
    @Nested
    class RemoveItems {

        @Test
        void removeItems_withValidRequest_shouldReturn204() {
            CartV1Dto.AddItemRequest addReq = new CartV1Dto.AddItemRequest(productId, null, 1);
            ResponseEntity<ApiResponse<CartV1Dto.CartItemResponse>> addRes = testRestTemplate.exchange(
                ENDPOINT_CART_ITEMS, HttpMethod.POST, new HttpEntity<>(addReq, authHeaders()),
                new ParameterizedTypeReference<>() {});
            Long cartItemId = addRes.getBody().data().id();

            CartV1Dto.RemoveItemsRequest removeReq = new CartV1Dto.RemoveItemsRequest(List.of(cartItemId));
            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_CART_ITEMS, HttpMethod.DELETE, new HttpEntity<>(removeReq, authHeaders()),
                new ParameterizedTypeReference<>() {});

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        }
    }
}

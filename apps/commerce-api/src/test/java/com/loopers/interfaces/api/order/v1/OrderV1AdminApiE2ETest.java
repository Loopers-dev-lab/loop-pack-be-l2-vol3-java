package com.loopers.interfaces.api.order.v1;

import static com.loopers.interfaces.api.order.v1.OrderAdminSteps.getOrder;
import static com.loopers.interfaces.api.order.v1.OrderAdminSteps.getOrders;
import static com.loopers.interfaces.api.order.v1.OrderSteps.createOrder;
import static com.loopers.interfaces.api.user.v1.UserSteps.signUp;
import static com.loopers.support.E2ETestHelper.assertErrorResponse;
import static com.loopers.support.E2ETestHelper.userAuthHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.support.error.ErrorType;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import com.loopers.domain.order.OrderStatus;
import com.loopers.interfaces.api.brand.v1.BrandDto;
import com.loopers.interfaces.api.brand.v1.BrandSteps;
import com.loopers.interfaces.api.product.v1.ProductDto;
import com.loopers.interfaces.api.product.v1.ProductSteps;
import com.loopers.interfaces.api.user.v1.UserV1Dto;
import com.loopers.support.BaseE2ETest;

class OrderV1AdminApiE2ETest extends BaseE2ETest {

    private static final String ORDER_ADMIN_ENDPOINT = "/api-admin/v1/orders";

    private HttpHeaders userHeaders;
    private Long productId;

    @BeforeEach
    void setUp() {
        var loginId = "testuser1";
        var loginPw = "Password1!";
        signUp(testRestTemplate, new UserV1Dto.SignUpRequest(loginId, loginPw, "테스트", "2000-01-01", "test@test.com"));
        userHeaders = userAuthHeaders(loginId, loginPw);

        var brandId = BrandSteps.createBrand(
                testRestTemplate,
                new BrandDto.CreateBrandRequest("테스트 브랜드", "https://example.com/logo.png", "브랜드 설명")
        );
        productId = ProductSteps.createProduct(
                testRestTemplate,
                new ProductDto.CreateProductRequest(brandId, "테스트 상품", "https://example.com/thumb.png", 10000L, 100L, "상품 설명")
        );
    }

    @DisplayName("GET /api-admin/v1/orders")
    @Nested
    class GetOrders {

        @DisplayName("주문이 존재하면, 전체 주문 목록이 반환된다.")
        @Test
        void returnsAllOrders_whenOrdersExist() {
            // arrange
            createOrder(testRestTemplate,
                    new OrderDto.CreateOrderRequest(List.of(new OrderDto.OrderItemRequest(productId, 1L))),
                    userHeaders);
            createOrder(testRestTemplate,
                    new OrderDto.CreateOrderRequest(List.of(new OrderDto.OrderItemRequest(productId, 2L))),
                    userHeaders);

            var url = UriComponentsBuilder.fromPath(ORDER_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 20)
                    .toUriString();

            // act
            var response = getOrders(testRestTemplate, url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(2),
                    () -> assertThat(response.getBody().data().hasNext()).isFalse()
            );
        }

        @DisplayName("생성일 내림차순으로 정렬된다.")
        @Test
        void returnsOrdersSortedByCreatedAtDesc() {
            // arrange
            var brandId = BrandSteps.createBrand(
                    testRestTemplate,
                    new BrandDto.CreateBrandRequest("두 번째 브랜드", "https://example.com/logo2.png", "설명")
            );
            var secondProductId = ProductSteps.createProduct(
                    testRestTemplate,
                    new ProductDto.CreateProductRequest(
                            brandId,
                            "두 번째 상품",
                            "https://example.com/thumb2.png",
                            20000L,
                            100L,
                            "설명"
                    )
            );

            var firstOrderId = createOrder(
                    testRestTemplate,
                    new OrderDto.CreateOrderRequest(List.of(new OrderDto.OrderItemRequest(productId, 1L))), 
                    userHeaders
            ).getBody().data().orderId();
            var secondOrderId = createOrder(
                    testRestTemplate,
                    new OrderDto.CreateOrderRequest(List.of(new OrderDto.OrderItemRequest(secondProductId, 1L))),
                    userHeaders
            ).getBody().data().orderId();

            var url = UriComponentsBuilder.fromPath(ORDER_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 20)
                    .toUriString();

            // act
            var response = getOrders(testRestTemplate, url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content())
                            .extracting(AdminOrderDto.OrderListResponse::orderId)
                            .containsExactly(secondOrderId, firstOrderId)
            );
        }

        @DisplayName("주문이 없으면, 빈 페이지가 반환된다.")
        @Test
        void returnsEmptyPage_whenNoOrdersExist() {
            // arrange
            var url = UriComponentsBuilder.fromPath(ORDER_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 20)
                    .toUriString();

            // act
            var response = getOrders(testRestTemplate, url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().hasNext()).isFalse()
            );
        }

        @DisplayName("모든 주문 상태의 주문이 조회된다.")
        @Test
        void returnsOrdersOfAllStatuses() {
            // arrange
            createOrder(testRestTemplate,
                    new OrderDto.CreateOrderRequest(List.of(new OrderDto.OrderItemRequest(productId, 1L))),
                    userHeaders);

            var url = UriComponentsBuilder.fromPath(ORDER_ADMIN_ENDPOINT)
                    .queryParam("page", 0)
                    .queryParam("size", 20)
                    .toUriString();

            // act
            var response = getOrders(testRestTemplate, url);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).status()).isEqualTo(OrderStatus.CREATED)
            );
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("주문이 존재하면, 주문 상세 정보와 마스킹된 주문자 이름이 반환된다.")
        @Test
        void returnsOrderDetailWithMaskedOrdererName_whenOrderExists() {
            // arrange
            var orderId = createOrder(
                    testRestTemplate,
                    new OrderDto.CreateOrderRequest(List.of(new OrderDto.OrderItemRequest(productId, 2L))),
                    userHeaders
            ).getBody().data().orderId();

            // act
            var response = getOrder(testRestTemplate, orderId);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().orderId()).isEqualTo(orderId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("테스트 상품"),
                    () -> assertThat(response.getBody().data().totalPrice()).isEqualTo(20000L),
                    () -> assertThat(response.getBody().data().orderItems()).hasSize(1),
                    () -> assertThat(response.getBody().data().orderer().name()).isEqualTo("테스*")
            );
        }

        @DisplayName("존재하지 않는 주문을 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            // act
            var response = getOrder(testRestTemplate, 999L);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.ORDER_NOT_FOUND);
        }

        @DisplayName("다른 사용자의 주문도 조회할 수 있다.")
        @Test
        void returnsAnyOrder_withoutOwnershipRestriction() {
            // arrange
            signUp(testRestTemplate,
                    new UserV1Dto.SignUpRequest("otheruser", "Password1!", "다른유저", "1990-01-01", "other@test.com"));
            var otherHeaders = userAuthHeaders("otheruser", "Password1!");
            var orderId = createOrder(
                    testRestTemplate,
                    new OrderDto.CreateOrderRequest(List.of(new OrderDto.OrderItemRequest(productId, 1L))),
                    otherHeaders
            ).getBody().data().orderId();

            // act
            var response = getOrder(testRestTemplate, orderId);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().orderId()).isEqualTo(orderId),
                    () -> assertThat(response.getBody().data().orderer().name()).isEqualTo("다른유*")
            );
        }
    }
}

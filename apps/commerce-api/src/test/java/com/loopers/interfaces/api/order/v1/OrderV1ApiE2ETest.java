package com.loopers.interfaces.api.order.v1;

import static com.loopers.interfaces.api.order.v1.OrderSteps.createOrder;
import static com.loopers.interfaces.api.order.v1.OrderSteps.getMyOrder;
import static com.loopers.interfaces.api.order.v1.OrderSteps.getMyOrders;
import static com.loopers.interfaces.api.user.v1.UserSteps.signUp;
import static com.loopers.support.E2ETestHelper.assertErrorResponse;
import static com.loopers.support.E2ETestHelper.userAuthHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.support.error.ErrorType;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import com.loopers.interfaces.api.brand.v1.BrandDto;
import com.loopers.interfaces.api.brand.v1.BrandSteps;
import com.loopers.interfaces.api.product.v1.ProductDto;
import com.loopers.interfaces.api.product.v1.ProductSteps;
import com.loopers.interfaces.api.user.v1.UserV1Dto;
import com.loopers.support.BaseE2ETest;

class OrderV1ApiE2ETest extends BaseE2ETest {

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
                new BrandDto.CreateBrandRequest("테스트브랜드", "https://example.com/logo.png", "브랜드 설명")
        );
        var productResponse = ProductSteps.createProduct(
                testRestTemplate,
                new ProductDto.CreateProductRequest(brandId, "테스트상품", "https://example.com/thumb.png", 10000L, 100L,
                        "상품 설명")
        );
        productId = productResponse.getBody().data().productId();
    }

    @DisplayName("POST /api/v1/orders")
    @Nested
    class CreateOrder {

        @DisplayName("유효한 상품과 수량으로 주문하면, 주문이 생성된다.")
        @Test
        void createsOrder_whenValidRequest() {
            // arrange
            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2L))
            );

            // act
            var response = createOrder(testRestTemplate, request, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(response.getBody().data().orderId()).isNotNull()
            );
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class GetMyOrders {

        private static final String ORDER_ENDPOINT = "/api/v1/orders";

        @DisplayName("주문이 존재하면, 주문 목록이 반환된다.")
        @Test
        void returnsOrderList_whenOrdersExist() {
            // arrange
            createOrder(testRestTemplate,
                    new OrderDto.CreateOrderRequest(List.of(new OrderDto.OrderItemRequest(productId, 2L))),
                    userHeaders);

            var today = LocalDate.now();
            var url = UriComponentsBuilder.fromPath(ORDER_ENDPOINT)
                    .queryParam("startDate", today.toString())
                    .queryParam("endDate", today.toString())
                    .toUriString();

            // act
            var response = getMyOrders(testRestTemplate, url, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).hasSize(1),
                    () -> assertThat(response.getBody().data().content().get(0).name()).isEqualTo("테스트상품"),
                    () -> assertThat(response.getBody().data().hasNext()).isFalse()
            );
        }

        @DisplayName("날짜 범위 밖이면, 빈 목록이 반환된다.")
        @Test
        void returnsEmptyPage_whenNoOrdersInDateRange() {
            // arrange
            createOrder(testRestTemplate,
                    new OrderDto.CreateOrderRequest(List.of(new OrderDto.OrderItemRequest(productId, 1L))),
                    userHeaders);

            var url = UriComponentsBuilder.fromPath(ORDER_ENDPOINT)
                    .queryParam("startDate", "2020-01-01")
                    .queryParam("endDate", "2020-01-02")
                    .toUriString();

            // act
            var response = getMyOrders(testRestTemplate, url, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isEmpty(),
                    () -> assertThat(response.getBody().data().hasNext()).isFalse()
            );
        }

        @DisplayName("startDate가 누락되면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenStartDateMissing() {
            // arrange
            var url = UriComponentsBuilder.fromPath(ORDER_ENDPOINT)
                    .queryParam("endDate", "2026-01-01")
                    .toUriString();

            // act
            var response = getMyOrders(testRestTemplate, url, userHeaders);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("endDate가 누락되면, 400 Bad Request를 반환한다.")
        @Test
        void returnsBadRequest_whenEndDateMissing() {
            // arrange
            var url = UriComponentsBuilder.fromPath(ORDER_ENDPOINT)
                    .queryParam("startDate", "2026-01-01")
                    .toUriString();

            // act
            var response = getMyOrders(testRestTemplate, url, userHeaders);

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/orders/{orderId}")
    @Nested
    class GetMyOrder {

        @DisplayName("주문 상세 정보를 조회하면, 주문 정보와 주문 항목이 반환된다.")
        @Test
        void returnsOrderDetail_whenValidOrderId() {
            // arrange
            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 2L))
            );
            var orderId = createOrder(testRestTemplate, request, userHeaders).getBody().data().orderId();

            // act
            var response = getMyOrder(testRestTemplate, orderId, userHeaders);

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().orderId()).isEqualTo(orderId),
                    () -> assertThat(response.getBody().data().name()).isEqualTo("테스트상품"),
                    () -> assertThat(response.getBody().data().totalPrice()).isEqualTo(20000L),
                    () -> assertThat(response.getBody().data().orderItems()).hasSize(1),
                    () -> assertThat(response.getBody().data().orderItems().get(0).productName()).isEqualTo("테스트상품"),
                    () -> assertThat(response.getBody().data().orderItems().get(0).quantity()).isEqualTo(2L),
                    () -> assertThat(response.getBody().data().orderItems().get(0).subtotal()).isEqualTo(20000L)
            );
        }

        @DisplayName("존재하지 않는 주문을 조회하면, 404 Not Found를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            // act
            var response = getMyOrder(testRestTemplate, 999L, userHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.NOT_FOUND, ErrorType.ORDER_NOT_FOUND);
        }

        @DisplayName("다른 사용자의 주문을 조회하면, 403 Forbidden을 반환한다.")
        @Test
        void returnsForbidden_whenOtherUsersOrder() {
            // arrange
            var request = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 1L))
            );
            var orderId = createOrder(testRestTemplate, request, userHeaders).getBody().data().orderId();

            signUp(testRestTemplate, new UserV1Dto.SignUpRequest("otheruser", "Password1!", "다른유저", "1995-05-05", "other@test.com"));
            var otherHeaders = userAuthHeaders("otheruser", "Password1!");

            // act
            var response = getMyOrder(testRestTemplate, orderId, otherHeaders);

            // assert
            assertErrorResponse(response, HttpStatus.FORBIDDEN, ErrorType.FORBIDDEN_ORDER_ACCESS);
        }
    }
}

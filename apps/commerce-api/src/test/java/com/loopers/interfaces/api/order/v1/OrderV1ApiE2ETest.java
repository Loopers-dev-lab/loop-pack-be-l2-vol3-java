package com.loopers.interfaces.api.order.v1;

import static com.loopers.interfaces.api.order.v1.OrderSteps.createOrder;
import static com.loopers.interfaces.api.user.v1.UserSteps.signUp;
import static com.loopers.support.E2ETestHelper.userAuthHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

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
}
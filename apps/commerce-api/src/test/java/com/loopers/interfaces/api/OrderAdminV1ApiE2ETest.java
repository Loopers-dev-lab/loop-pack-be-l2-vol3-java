package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Order Admin API E2E 테스트")
class OrderAdminV1ApiE2ETest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private BrandRepository brandRepository;

    @Autowired
    private ProductRepository productRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    @DisplayName("GET /api-admin/v1/orders - 관리자 주문 목록")
    class GetAllOrders {

        @Test
        @DisplayName("성공: 모든 주문 목록을 조회한다")
        void getAllOrders_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Product product = productRepository.save(Product.create(brand.getId(), "상품", null, new BigDecimal("10000"), 100, null));

            // 사용자1 주문
            Long userId1 = 1L;
            OrderV1Dto.CreateRequest request1 = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1))
            );
            HttpHeaders headers1 = new HttpHeaders();
            headers1.set("X-User-Id", userId1.toString());
            restTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(request1, headers1),
                    new ParameterizedTypeReference<ApiResponse<OrderV1Dto.Response>>() {}
            );

            // 사용자2 주문
            Long userId2 = 2L;
            OrderV1Dto.CreateRequest request2 = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 2))
            );
            HttpHeaders headers2 = new HttpHeaders();
            headers2.set("X-User-Id", userId2.toString());
            restTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(request2, headers2),
                    new ParameterizedTypeReference<ApiResponse<OrderV1Dto.Response>>() {}
            );

            HttpHeaders adminHeaders = new HttpHeaders();
            adminHeaders.set("X-Loopers-Ldap", "loopers.admin");

            // When
            ResponseEntity<ApiResponse<OrderV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api-admin/v1/orders?page=0&size=10",
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            OrderV1Dto.PageResponse data = response.getBody().data();
            assertThat(data.content()).hasSize(2);
            assertThat(data.totalElements()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("GET /api-admin/v1/orders/{orderId} - 관리자 주문 상세")
    class GetOrder {

        @Test
        @DisplayName("성공: 주문 상세를 조회한다")
        void getOrder_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Product product = productRepository.save(Product.create(brand.getId(), "상품", null, new BigDecimal("10000"), 100, null));

            Long userId = 1L;
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 3))
            );
            HttpHeaders userHeaders = new HttpHeaders();
            userHeaders.set("X-User-Id", userId.toString());

            ResponseEntity<ApiResponse<OrderV1Dto.Response>> createResponse = restTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(request, userHeaders),
                    new ParameterizedTypeReference<>() {}
            );

            Long orderId = createResponse.getBody().data().id();

            HttpHeaders adminHeaders = new HttpHeaders();
            adminHeaders.set("X-Loopers-Ldap", "loopers.admin");

            // When
            ResponseEntity<ApiResponse<OrderV1Dto.Response>> response = restTemplate.exchange(
                    "/api-admin/v1/orders/" + orderId,
                    HttpMethod.GET,
                    new HttpEntity<>(adminHeaders),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            OrderV1Dto.Response data = response.getBody().data();
            assertThat(data.id()).isEqualTo(orderId);
            assertThat(data.userId()).isEqualTo(userId);
            assertThat(data.orderItems()).hasSize(1);
            assertThat(data.orderItems().get(0).quantity()).isEqualTo(3);
        }
    }
}

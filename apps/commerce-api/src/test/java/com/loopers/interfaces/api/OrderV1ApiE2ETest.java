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
@DisplayName("Order Customer API E2E 테스트")
class OrderV1ApiE2ETest {

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
    @DisplayName("POST /api/v1/orders - 주문 생성")
    class CreateOrder {

        @Test
        @DisplayName("성공: 주문을 생성한다")
        void createOrder_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Product product1 = productRepository.save(Product.create(brand.getId(), "상품1", null, new BigDecimal("10000"), 100, null));
            Product product2 = productRepository.save(Product.create(brand.getId(), "상품2", null, new BigDecimal("20000"), 50, null));

            Long userId = 1L;
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(
                            new OrderV1Dto.OrderItemRequest(product1.getId(), 2),
                            new OrderV1Dto.OrderItemRequest(product2.getId(), 1)
                    )
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When
            ResponseEntity<ApiResponse<OrderV1Dto.Response>> response = restTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            OrderV1Dto.Response data = response.getBody().data();
            assertThat(data.id()).isNotNull();
            assertThat(data.userId()).isEqualTo(userId);
            assertThat(data.totalAmount()).isEqualByComparingTo(new BigDecimal("40000")); // 10000*2 + 20000*1
            assertThat(data.orderItems()).hasSize(2);

            // 재고 감소 확인
            Product updatedProduct1 = productRepository.findById(product1.getId()).get();
            assertThat(updatedProduct1.getStock()).isEqualTo(98); // 100 - 2

            Product updatedProduct2 = productRepository.findById(product2.getId()).get();
            assertThat(updatedProduct2.getStock()).isEqualTo(49); // 50 - 1
        }

        @Test
        @DisplayName("실패: 재고가 부족하면 400 BAD_REQUEST를 반환한다")
        void createOrder_InsufficientStock() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Product product = productRepository.save(Product.create(brand.getId(), "상품", null, new BigDecimal("10000"), 5, null));

            Long userId = 1L;
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 10)) // 재고보다 많이 주문
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders - 내 주문 목록")
    class GetMyOrders {

        @Test
        @DisplayName("성공: 내 주문 목록을 조회한다")
        void getMyOrders_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Product product = productRepository.save(Product.create(brand.getId(), "상품", null, new BigDecimal("10000"), 100, null));

            Long userId = 1L;
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1))
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // 주문 생성
            restTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<ApiResponse<OrderV1Dto.Response>>() {}
            );

            // When
            ResponseEntity<ApiResponse<OrderV1Dto.PageResponse>> response = restTemplate.exchange(
                    "/api/v1/orders?page=0&size=10",
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            OrderV1Dto.PageResponse data = response.getBody().data();
            assertThat(data.content()).hasSize(1);
            assertThat(data.totalElements()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("GET /api/v1/orders/{orderId} - 내 주문 상세")
    class GetMyOrder {

        @Test
        @DisplayName("성공: 내 주문 상세를 조회한다")
        void getMyOrder_Success() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Product product = productRepository.save(Product.create(brand.getId(), "상품", null, new BigDecimal("10000"), 100, null));

            Long userId = 1L;
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 2))
            );

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // 주문 생성
            ResponseEntity<ApiResponse<OrderV1Dto.Response>> createResponse = restTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers),
                    new ParameterizedTypeReference<>() {}
            );

            Long orderId = createResponse.getBody().data().id();

            // When
            ResponseEntity<ApiResponse<OrderV1Dto.Response>> response = restTemplate.exchange(
                    "/api/v1/orders/" + orderId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();

            OrderV1Dto.Response data = response.getBody().data();
            assertThat(data.id()).isEqualTo(orderId);
            assertThat(data.userId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("실패: 다른 사용자의 주문은 조회할 수 없다")
        void getMyOrder_Forbidden() {
            // Given
            Brand brand = brandRepository.save(Brand.create("샤넬", null, null));
            Product product = productRepository.save(Product.create(brand.getId(), "상품", null, new BigDecimal("10000"), 100, null));

            Long userId1 = 1L;
            OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1))
            );

            HttpHeaders headers1 = new HttpHeaders();
            headers1.set("X-User-Id", userId1.toString());

            // 사용자1이 주문 생성
            ResponseEntity<ApiResponse<OrderV1Dto.Response>> createResponse = restTemplate.exchange(
                    "/api/v1/orders",
                    HttpMethod.POST,
                    new HttpEntity<>(request, headers1),
                    new ParameterizedTypeReference<>() {}
            );

            Long orderId = createResponse.getBody().data().id();

            // 사용자2가 조회 시도
            Long userId2 = 2L;
            HttpHeaders headers2 = new HttpHeaders();
            headers2.set("X-User-Id", userId2.toString());

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api/v1/orders/" + orderId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers2),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
            assertThat(response.getBody()).isNotNull();
            assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL);
            assertThat(response.getBody().meta().errorCode()).isEqualTo("Forbidden");
        }

        @Test
        @DisplayName("실패: 존재하지 않는 주문은 404 NOT_FOUND를 반환한다")
        void getMyOrder_NotFound() {
            // Given
            Long userId = 1L;
            Long nonExistentOrderId = 999L;

            HttpHeaders headers = new HttpHeaders();
            headers.set("X-User-Id", userId.toString());

            // When
            ResponseEntity<ApiResponse<Void>> response = restTemplate.exchange(
                    "/api/v1/orders/" + nonExistentOrderId,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {}
            );

            // Then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }
}

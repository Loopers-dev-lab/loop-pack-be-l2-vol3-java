package com.loopers.interfaces.api.order;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Quantity;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.error.ErrorType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderAdminV1ApiE2ETest {

    private static final String HEADER_ADMIN_LDAP = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    private static final String ENDPOINT_GET_ORDERS = "/api-admin/v1/orders";
    private static final Function<Long, String> ENDPOINT_GET_ORDER = id -> "/api-admin/v1/orders/" + id;

    private static final Long USER_ID = 1L;
    private static final Long NOT_EXISTED_ORDER_ID = 999L;
    private static final String PRODUCT_NAME = "나이키 에어맥스";
    private static final int VALID_PRICE = 10000;
    private static final int VALID_STOCK = 100;

    private final TestRestTemplate testRestTemplate;
    private final DatabaseCleanUp databaseCleanUp;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;

    @Autowired
    public OrderAdminV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            DatabaseCleanUp databaseCleanUp,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            OrderJpaRepository orderJpaRepository
    ) {
        this.testRestTemplate = testRestTemplate;
        this.databaseCleanUp = databaseCleanUp;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    HttpHeaders createAdminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_ADMIN_LDAP, ADMIN_LDAP_VALUE);
        return headers;
    }

    Order createSavedOrder(Product product, String brandName) {
        Money originalAmount = product.getPrice();
        Money discountAmount = new Money(0);
        return orderJpaRepository.save(new Order(USER_ID,
                List.of(new OrderItem(product.getId(), new Quantity(1), product.getName(), brandName, product.getPrice())),
                null, originalAmount, discountAmount));
    }

    @DisplayName("GET /api-admin/v1/orders")
    @Nested
    class GetOrders {

        @DisplayName("관리자가 전체 주문 목록을 페이지 단위로 조회하면, 200 OK와 주문 목록을 반환한다.")
        @Test
        void returnsAllOrders_whenAdminRequests() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            createSavedOrder(product, brand.getName());
            createSavedOrder(product, brand.getName());

            // act
            ParameterizedTypeReference<ApiResponse<OrderAdminV1Dto.OrderListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_GET_ORDERS + "?page=0&size=20",
                    HttpMethod.GET,
                    new HttpEntity<>(createAdminHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().orders()).hasSize(2)
            );
        }

        @DisplayName("관리자 인증 헤더 없이 조회하면, 401 UNAUTHORIZED를 반환한다.")
        @Test
        void returnsUnauthorized_whenAdminHeaderMissing() {
            // act
            ParameterizedTypeReference<ApiResponse<OrderAdminV1Dto.OrderListResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderListResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_GET_ORDERS + "?page=0&size=20",
                    HttpMethod.GET,
                    HttpEntity.EMPTY,
                    responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.UNAUTHORIZED.getStatus());
        }
    }

    @DisplayName("GET /api-admin/v1/orders/{orderId}")
    @Nested
    class GetOrder {

        @DisplayName("관리자가 주문 상세를 조회하면, 200 OK와 주문 상세 정보를 반환한다.")
        @Test
        void returnsOrderDetail_whenAdminRequests() {
            // arrange
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), PRODUCT_NAME, new Money(VALID_PRICE), new Stock(VALID_STOCK)));
            Order order = createSavedOrder(product, brand.getName());

            // act
            ParameterizedTypeReference<ApiResponse<OrderAdminV1Dto.OrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_GET_ORDER.apply(order.getId()),
                    HttpMethod.GET,
                    new HttpEntity<>(createAdminHeaders()),
                    responseType
            );

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getBody().data().orderId()).isEqualTo(order.getId()),
                    () -> assertThat(response.getBody().data().items()).hasSize(1)
            );
        }

        @DisplayName("존재하지 않는 주문을 조회하면, 404 NOT_FOUND를 반환한다.")
        @Test
        void returnsNotFound_whenOrderDoesNotExist() {
            // act
            ParameterizedTypeReference<ApiResponse<OrderAdminV1Dto.OrderResponse>> responseType =
                    new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<OrderAdminV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_GET_ORDER.apply(NOT_EXISTED_ORDER_ID),
                    HttpMethod.GET,
                    new HttpEntity<>(createAdminHeaders()),
                    responseType
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.NOT_FOUND.getStatus());
        }
    }
}

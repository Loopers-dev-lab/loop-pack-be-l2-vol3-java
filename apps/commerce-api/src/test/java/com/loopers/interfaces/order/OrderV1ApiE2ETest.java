package com.loopers.interfaces.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.domain.product.ProductModel;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.order.dto.OrderResponse;
import com.loopers.interfaces.user.dto.UserV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import java.util.List;
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

@DisplayName("Order V1 API E2E 테스트")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderV1ApiE2ETest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final TestRestTemplate testRestTemplate;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final OrderJpaRepository orderJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;
    private Long orderId;
    private Long orderItemId;

    @Autowired
    public OrderV1ApiE2ETest(
        TestRestTemplate testRestTemplate,
        BrandJpaRepository brandJpaRepository,
        ProductJpaRepository productJpaRepository,
        OrderJpaRepository orderJpaRepository,
        DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.orderJpaRepository = orderJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    @BeforeEach
    void setUp() {
        // 사용자 등록
        UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
            "testuser1", "Test1234!", "홍길동", "19900101", "test@example.com");
        ResponseEntity<ApiResponse<UserV1Dto.SignupResponse>> signupResponse =
                testRestTemplate.exchange(
                        "/api/v1/users/signup", HttpMethod.POST,
                        new HttpEntity<>(signupRequest),
                        new ParameterizedTypeReference<>() {});
        userId = signupResponse.getBody().data().id();

        // 브랜드 + 상품
        BrandModel brand = brandJpaRepository.save(BrandModel.create("ACNE STUDIOS"));
        ProductModel product = productJpaRepository.save(
                ProductModel.create(brand.getId(), "오버사이즈 코트", 50000, 100));
        productId = product.getId();

        // 주문 직접 생성 (재고 차감 시뮬레이션)
        product.decreaseStock(2);
        productJpaRepository.save(product);

        OrderModel order = orderJpaRepository.save(
                OrderModel.create(userId, List.of(
                        OrderItemModel.create(productId, 50000, 2, "오버사이즈 코트", ("ACNE STUDIOS")))));
        orderId = order.getId();
        orderItemId = order.getItems().get(0).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, "testuser1");
        headers.set(HEADER_LOGIN_PW, "Test1234!");
        return headers;
    }

    private String cancelEndpoint(Long orderId, Long orderItemId) {
        return "/api/v1/orders/" + orderId + "/items/" + orderItemId + "/cancel";
    }

    @DisplayName("PATCH /api/v1/orders/{orderId}/items/{orderItemId}/cancel")
    @Nested
    class CancelItem {

        @DisplayName("본인 주문의 아이템을 취소하면, 성공 응답을 반환한다.")
        @Test
        void returnsSuccess_whenValidRequest() {
            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(
                            cancelEndpoint(orderId, orderItemId), HttpMethod.PATCH,
                            new HttpEntity<>(null, authHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK));
        }

        @DisplayName("취소 후 재고가 복구된다.")
        @Test
        void restoresStock_afterCancel() {
            // act
            testRestTemplate.exchange(
                    cancelEndpoint(orderId, orderItemId), HttpMethod.PATCH,
                    new HttpEntity<>(null, authHeaders()),
                    new ParameterizedTypeReference<ApiResponse<Object>>() {});

            // assert
            ProductModel product = productJpaRepository.findById(productId).orElseThrow();
            assertThat(product.getStock()).isEqualTo(100);
        }

        @DisplayName("취소 후 주문 상세에서 totalPrice가 재계산된다.")
        @Test
        void recalculatesTotalPrice_afterCancel() {
            // act
            testRestTemplate.exchange(
                    cancelEndpoint(orderId, orderItemId), HttpMethod.PATCH,
                    new HttpEntity<>(null, authHeaders()),
                    new ParameterizedTypeReference<ApiResponse<Object>>() {});

            // assert
            ResponseEntity<ApiResponse<OrderResponse.OrderDetail>> detailResponse =
                    testRestTemplate.exchange(
                            "/api/v1/orders/" + orderId, HttpMethod.GET,
                            new HttpEntity<>(null, authHeaders()),
                            new ParameterizedTypeReference<>() {});

            assertThat(detailResponse.getBody().data().totalPrice()).isEqualTo(0);
        }

        @DisplayName("이미 취소된 아이템을 다시 취소하면, 400 응답을 반환한다.")
        @Test
        void throwsBadRequest_whenAlreadyCancelled() {
            // arrange
            testRestTemplate.exchange(
                    cancelEndpoint(orderId, orderItemId), HttpMethod.PATCH,
                    new HttpEntity<>(null, authHeaders()),
                    new ParameterizedTypeReference<ApiResponse<Object>>() {});

            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(
                            cancelEndpoint(orderId, orderItemId), HttpMethod.PATCH,
                            new HttpEntity<>(null, authHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @DisplayName("존재하지 않는 주문 ID로 취소하면, 404 응답을 반환한다.")
        @Test
        void throwsNotFound_whenOrderNotFound() {
            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(
                            cancelEndpoint(999L, 999L), HttpMethod.PATCH,
                            new HttpEntity<>(null, authHeaders()),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }

        @DisplayName("타인의 주문을 취소하면, 403 응답을 반환한다.")
        @Test
        void throwsForbidden_whenNotOwner() {
            // arrange
            UserV1Dto.SignupRequest otherUser = new UserV1Dto.SignupRequest(
                    "otheruser1", "Test1234!", "김철수", "19950101", "other@example.com");
            testRestTemplate.exchange(
                    "/api/v1/users/signup", HttpMethod.POST,
                    new HttpEntity<>(otherUser),
                    new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignupResponse>>() {});

            HttpHeaders otherHeaders = new HttpHeaders();
            otherHeaders.set(HEADER_LOGIN_ID, "otheruser1");
            otherHeaders.set(HEADER_LOGIN_PW, "Test1234!");

            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(
                            cancelEndpoint(orderId, orderItemId), HttpMethod.PATCH,
                            new HttpEntity<>(null, otherHeaders),
                            new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        }

        @DisplayName("인증 헤더가 없으면, 401 응답을 반환한다.")
        @Test
        void throwsUnauthorized_whenNoAuthHeaders() {
            // act
            ResponseEntity<ApiResponse<Object>> response =
                    testRestTemplate.exchange(
                            cancelEndpoint(1L, 1L), HttpMethod.PATCH,
                            null,
                            new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }
}

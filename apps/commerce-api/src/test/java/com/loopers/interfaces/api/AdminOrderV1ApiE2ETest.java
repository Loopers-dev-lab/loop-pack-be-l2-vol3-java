package com.loopers.interfaces.api;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Product;
import com.loopers.domain.user.User;
import com.loopers.domain.user.UserFixture;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminOrderV1ApiE2ETest {

    private static final String USER_ENDPOINT = "/api/v1/orders";
    private static final String ADMIN_ENDPOINT = "/api-admin/v1/orders";
    private static final String LDAP_HEADER = "X-Loopers-Ldap";
    private static final String LDAP_VALUE = "loopers.admin";
    private static final String RAW_PASSWORD = "TestPass1!";

    private final TestRestTemplate testRestTemplate;
    private final UserJpaRepository userJpaRepository;
    private final BrandJpaRepository brandJpaRepository;
    private final ProductJpaRepository productJpaRepository;
    private final DatabaseCleanUp databaseCleanUp;
    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();

    @Autowired
    public AdminOrderV1ApiE2ETest(
            TestRestTemplate testRestTemplate,
            UserJpaRepository userJpaRepository,
            BrandJpaRepository brandJpaRepository,
            ProductJpaRepository productJpaRepository,
            DatabaseCleanUp databaseCleanUp
    ) {
        this.testRestTemplate = testRestTemplate;
        this.userJpaRepository = userJpaRepository;
        this.brandJpaRepository = brandJpaRepository;
        this.productJpaRepository = productJpaRepository;
        this.databaseCleanUp = databaseCleanUp;
    }

    private User savedUser;
    private Product savedProduct;

    @BeforeEach
    void setUp() {
        String encodedPassword = bCryptPasswordEncoder.encode(RAW_PASSWORD);
        savedUser = userJpaRepository.save(
                UserFixture.builder()
                           .loginId("adminOrderTestUser")
                           .password(encodedPassword)
                           .build()
        );

        Brand brand = brandJpaRepository.save(Brand.create("나이키", "스포츠"));
        savedProduct = productJpaRepository.save(Product.create(brand.getId(), "에어맥스", null, 150000, 10));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set(LDAP_HEADER, LDAP_VALUE);
        return headers;
    }

    private HttpHeaders userHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", savedUser.getLoginId());
        headers.set("X-Loopers-LoginPw", RAW_PASSWORD);
        return headers;
    }

    private Long createOrder() {
        OrderV1Dto.CreateRequest request = new OrderV1Dto.CreateRequest(
                List.of(new OrderV1Dto.OrderItemRequest(savedProduct.getId(), 1)),
                null
        );
        HttpEntity<OrderV1Dto.CreateRequest> entity = new HttpEntity<>(request, userHeaders());
        ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response =
                testRestTemplate.exchange(USER_ENDPOINT, HttpMethod.POST, entity, new ParameterizedTypeReference<>() {});
        return response.getBody().data().id();
    }

    @DisplayName("어드민 주문 목록 조회 시")
    @Nested
    class GetOrders {

        @DisplayName("주문 목록을 조회하면, 200 OK와 페이징 결과를 반환한다.")
        @Test
        void returnsOk_withPagedOrders() {
            // arrange
            createOrder();
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<PageResponse<OrderV1Dto.OrderResponse>>> response =
                    testRestTemplate.exchange(ADMIN_ENDPOINT, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().content()).isNotEmpty()
            );
        }
    }

    @DisplayName("어드민 주문 상세 조회 시")
    @Nested
    class GetOrder {

        @DisplayName("주문이 존재하면, 200 OK와 주문 상세를 반환한다.")
        @Test
        void returnsOk_whenOrderExists() {
            // arrange
            Long orderId = createOrder();
            HttpEntity<Void> entity = new HttpEntity<>(adminHeaders());

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderDetailResponse>> response =
                    testRestTemplate.exchange(ADMIN_ENDPOINT + "/" + orderId, HttpMethod.GET, entity, new ParameterizedTypeReference<>() {});

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().items()).isNotEmpty(),
                    () -> assertThat(response.getBody().data().items().get(0).productId()).isEqualTo(savedProduct.getId())
            );
        }
    }
}

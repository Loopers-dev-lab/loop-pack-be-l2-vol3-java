package com.loopers.interfaces.api.admin;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandRepository;
import com.loopers.domain.common.Money;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberRepository;
import com.loopers.domain.member.PasswordEncoder;
import com.loopers.domain.member.vo.BirthDate;
import com.loopers.domain.member.vo.Email;
import com.loopers.domain.member.vo.MemberId;
import com.loopers.domain.member.vo.Name;
import com.loopers.domain.member.vo.Password;
import com.loopers.application.order.OrderAppService;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.OptionRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderDto;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AdminOrderApiE2ETest {

    private static final String ADMIN_ORDERS_ENDPOINT = "/api/admin/v1/orders";
    private static final String USER_ORDERS_ENDPOINT = "/api/v1/orders";
    private static final String ADMIN_LDAP = "admin-test";
    private static final String TEST_PASSWORD = "Password1!";

    @Autowired
    private TestRestTemplate testRestTemplate;
    @Autowired
    private BrandRepository brandRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private OptionRepository optionRepository;
    @Autowired
    private MemberRepository memberRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private OrderAppService orderAppService;
    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Option testOption;

    @BeforeEach
    void setUp() {
        Brand brand = brandRepository.save(Brand.create("테스트 브랜드"));
        Product product = productRepository.save(Product.create(brand.getId(), "테스트 상품", Money.of(BigDecimal.valueOf(10000))));
        testOption = optionRepository.save(Option.create(product.getId(), "기본 옵션", Money.of(BigDecimal.valueOf(1000)), 100));
        createTestMember("testuser", TEST_PASSWORD);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    private Long createOrder() {
        OrderDto.CreateDirectRequest request = new OrderDto.CreateDirectRequest(testOption.getId(), 2);
        HttpEntity<OrderDto.CreateDirectRequest> httpEntity = createUserHttpEntity(request, "testuser", TEST_PASSWORD);
        ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                USER_ORDERS_ENDPOINT + "/direct", HttpMethod.POST, httpEntity, new ParameterizedTypeReference<>() {}
        );
        return response.getBody().data().orderId();
    }

    @Nested
    @DisplayName("GET /api/admin/v1/orders")
    class GetAllOrdersTest {

        @Test
        @DisplayName("전체 주문 목록을 조회할 수 있다")
        void getAll_success() {
            // arrange
            createOrder();
            createOrder();
            HttpEntity<Void> httpEntity = createAdminHttpEntity(null);

            // act
            ResponseEntity<ApiResponse<AdminOrderDto.OrderListResponse>> response = testRestTemplate.exchange(
                    ADMIN_ORDERS_ENDPOINT, HttpMethod.GET, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().orders()).hasSize(2)
            );
        }

        @Test
        @DisplayName("Admin 인증 없이 요청하면 401 응답을 받는다")
        void getAll_unauthorized() {
            // arrange
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Void> httpEntity = new HttpEntity<>(null, headers);

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ADMIN_ORDERS_ENDPOINT, HttpMethod.GET, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    @Nested
    @DisplayName("GET /api/admin/v1/orders/{id}")
    class GetOrderByIdTest {

        @Test
        @DisplayName("주문 상세를 조회할 수 있다")
        void getById_success() {
            // arrange
            Long orderId = createOrder();
            HttpEntity<Void> httpEntity = createAdminHttpEntity(null);

            // act
            ResponseEntity<ApiResponse<AdminOrderDto.OrderResponse>> response = testRestTemplate.exchange(
                    ADMIN_ORDERS_ENDPOINT + "/" + orderId,
                    HttpMethod.GET, httpEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().orderId()).isEqualTo(orderId),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(OrderStatus.PENDING)
            );
        }
    }

    @Nested
    @DisplayName("주문 상태 변경 흐름")
    class OrderStateTransitionTest {

        @Test
        @DisplayName("PENDING → PAID → PREPARING → SHIPPED → DELIVERED 전체 흐름이 동작한다")
        void fullOrderLifecycle() {
            // arrange
            Long orderId = createOrder();

            // pay (PENDING → PAID) - OrderAppService를 직접 호출하여 결제 처리
            orderAppService.pay(orderId);

            HttpEntity<Void> adminEntity = createAdminHttpEntity(null);

            // prepare (PAID → PREPARING)
            ResponseEntity<ApiResponse<AdminOrderDto.OrderResponse>> prepareResponse = testRestTemplate.exchange(
                    ADMIN_ORDERS_ENDPOINT + "/" + orderId + "/prepare",
                    HttpMethod.POST, adminEntity, new ParameterizedTypeReference<>() {}
            );
            assertThat(prepareResponse.getBody().data().status()).isEqualTo(OrderStatus.PREPARING);

            // ship (PREPARING → SHIPPED)
            ResponseEntity<ApiResponse<AdminOrderDto.OrderResponse>> shipResponse = testRestTemplate.exchange(
                    ADMIN_ORDERS_ENDPOINT + "/" + orderId + "/ship",
                    HttpMethod.POST, adminEntity, new ParameterizedTypeReference<>() {}
            );
            assertThat(shipResponse.getBody().data().status()).isEqualTo(OrderStatus.SHIPPED);

            // deliver (SHIPPED → DELIVERED)
            ResponseEntity<ApiResponse<AdminOrderDto.OrderResponse>> deliverResponse = testRestTemplate.exchange(
                    ADMIN_ORDERS_ENDPOINT + "/" + orderId + "/deliver",
                    HttpMethod.POST, adminEntity, new ParameterizedTypeReference<>() {}
            );
            assertThat(deliverResponse.getBody().data().status()).isEqualTo(OrderStatus.DELIVERED);
        }

        @Test
        @DisplayName("잘못된 상태 전이 시 400 응답을 받는다 (PENDING → PREPARING)")
        void invalidStateTransition() {
            // arrange - PENDING 상태의 주문
            Long orderId = createOrder();
            HttpEntity<Void> adminEntity = createAdminHttpEntity(null);

            // act - PENDING에서 바로 PREPARING 시도
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ADMIN_ORDERS_ENDPOINT + "/" + orderId + "/prepare",
                    HttpMethod.POST, adminEntity, new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private Member createTestMember(String memberId, String rawPassword) {
        String encodedPassword = passwordEncoder.encode(rawPassword);
        Member member = Member.create(
                new MemberId(memberId),
                Password.ofEncoded(encodedPassword),
                new Name("테스트"),
                new Email(memberId + "@test.com"),
                new BirthDate("1997-01-01")
        );
        return memberRepository.save(member);
    }

    private <T> HttpEntity<T> createAdminHttpEntity(T body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-Ldap", ADMIN_LDAP);
        return new HttpEntity<>(body, headers);
    }

    private <T> HttpEntity<T> createUserHttpEntity(T body, String memberId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", memberId);
        headers.set("X-Loopers-LoginPw", password);
        return new HttpEntity<>(body, headers);
    }
}

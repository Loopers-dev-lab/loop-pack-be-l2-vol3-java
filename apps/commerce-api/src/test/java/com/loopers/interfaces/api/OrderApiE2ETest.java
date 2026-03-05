package com.loopers.interfaces.api;

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
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Option;
import com.loopers.domain.product.OptionRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.interfaces.api.cart.CartDto;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderApiE2ETest {

    private static final String ORDERS_ENDPOINT = "/api/v1/orders";
    private static final String CART_ENDPOINT = "/api/v1/cart";

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
    private DatabaseCleanUp databaseCleanUp;

    private Brand testBrand;
    private Product testProduct;
    private Option testOption;
    private Member testMember;
    private static final String TEST_PASSWORD = "Password1!";

    @BeforeEach
    void setUp() {
        testBrand = brandRepository.save(Brand.create("테스트 브랜드"));
        testProduct = productRepository.save(Product.create(testBrand.getId(), "테스트 상품", Money.of(BigDecimal.valueOf(10000))));
        testOption = optionRepository.save(Option.create(testProduct.getId(), "기본 옵션", Money.of(BigDecimal.valueOf(1000)), 100));
        testMember = createTestMember("testuser", TEST_PASSWORD);
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/orders (장바구니 주문)")
    @Nested
    class CreateOrderFromCart {

        @DisplayName("장바구니 항목으로 주문을 생성할 수 있다.")
        @Test
        void createOrderFromCart_success() {
            // arrange - 먼저 장바구니에 담기
            CartDto.AddRequest addRequest = new CartDto.AddRequest(testOption.getId(), 2);
            HttpEntity<CartDto.AddRequest> addEntity = createHttpEntity(addRequest, "testuser", TEST_PASSWORD);
            ResponseEntity<ApiResponse<CartDto.AddResponse>> addResponse = testRestTemplate.exchange(
                    CART_ENDPOINT, HttpMethod.POST, addEntity, new ParameterizedTypeReference<>() {}
            );
            Long cartItemId = addResponse.getBody().data().cartItemId();

            OrderDto.CreateFromCartRequest orderRequest = new OrderDto.CreateFromCartRequest(List.of(cartItemId));
            HttpEntity<OrderDto.CreateFromCartRequest> orderEntity = createHttpEntity(orderRequest, "testuser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                    ORDERS_ENDPOINT,
                    HttpMethod.POST,
                    orderEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.SUCCESS),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(OrderStatus.PENDING),
                    () -> assertThat(response.getBody().data().items()).hasSize(1),
                    () -> assertThat(response.getBody().data().items().get(0).productName()).isEqualTo("테스트 상품"),
                    () -> assertThat(response.getBody().data().items().get(0).quantity()).isEqualTo(2)
            );

            // 장바구니가 비워졌는지 확인
            HttpEntity<Void> cartEntity = createHttpEntity(null, "testuser", TEST_PASSWORD);
            ResponseEntity<ApiResponse<CartDto.CartResponse>> cartResponse = testRestTemplate.exchange(
                    CART_ENDPOINT, HttpMethod.GET, cartEntity, new ParameterizedTypeReference<>() {}
            );
            assertThat(cartResponse.getBody().data().items()).isEmpty();
        }

        @DisplayName("주문 후 재고가 차감된다.")
        @Test
        void createOrderFromCart_decreases_stock() {
            // arrange
            int initialStock = testOption.getStock();

            CartDto.AddRequest addRequest = new CartDto.AddRequest(testOption.getId(), 5);
            HttpEntity<CartDto.AddRequest> addEntity = createHttpEntity(addRequest, "testuser", TEST_PASSWORD);
            ResponseEntity<ApiResponse<CartDto.AddResponse>> addResponse = testRestTemplate.exchange(
                    CART_ENDPOINT, HttpMethod.POST, addEntity, new ParameterizedTypeReference<>() {}
            );
            Long cartItemId = addResponse.getBody().data().cartItemId();

            OrderDto.CreateFromCartRequest orderRequest = new OrderDto.CreateFromCartRequest(List.of(cartItemId));
            HttpEntity<OrderDto.CreateFromCartRequest> orderEntity = createHttpEntity(orderRequest, "testuser", TEST_PASSWORD);

            // act
            testRestTemplate.exchange(ORDERS_ENDPOINT, HttpMethod.POST, orderEntity, new ParameterizedTypeReference<ApiResponse<OrderDto.OrderResponse>>() {});

            // assert - 재고 확인
            Option updatedOption = optionRepository.findById(testOption.getId()).orElseThrow();
            assertThat(updatedOption.getStock()).isEqualTo(initialStock - 5);
        }
    }

    @DisplayName("POST /api/v1/orders/direct (바로구매)")
    @Nested
    class CreateOrderDirect {

        @DisplayName("바로구매로 주문을 생성할 수 있다.")
        @Test
        void createOrderDirect_success() {
            // arrange
            OrderDto.CreateDirectRequest request = new OrderDto.CreateDirectRequest(testOption.getId(), 3);
            HttpEntity<OrderDto.CreateDirectRequest> httpEntity = createHttpEntity(request, "testuser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                    ORDERS_ENDPOINT + "/direct",
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(OrderStatus.PENDING),
                    () -> assertThat(response.getBody().data().items()).hasSize(1),
                    () -> assertThat(response.getBody().data().items().get(0).quantity()).isEqualTo(3)
            );
        }

        @DisplayName("재고보다 많은 수량을 주문하면 400 응답을 받는다.")
        @Test
        void createOrderDirect_fail_insufficient_stock() {
            // arrange
            OrderDto.CreateDirectRequest request = new OrderDto.CreateDirectRequest(testOption.getId(), 999);
            HttpEntity<OrderDto.CreateDirectRequest> httpEntity = createHttpEntity(request, "testuser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ORDERS_ENDPOINT + "/direct",
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @DisplayName("GET /api/v1/orders")
    @Nested
    class GetOrders {

        @DisplayName("주문 내역을 조회할 수 있다.")
        @Test
        void getOrders_success() {
            // arrange - 주문 생성
            OrderDto.CreateDirectRequest request = new OrderDto.CreateDirectRequest(testOption.getId(), 2);
            HttpEntity<OrderDto.CreateDirectRequest> orderEntity = createHttpEntity(request, "testuser", TEST_PASSWORD);
            testRestTemplate.exchange(ORDERS_ENDPOINT + "/direct", HttpMethod.POST, orderEntity, new ParameterizedTypeReference<ApiResponse<OrderDto.OrderResponse>>() {});

            HttpEntity<Void> httpEntity = createHttpEntity(null, "testuser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderListResponse>> response = testRestTemplate.exchange(
                    ORDERS_ENDPOINT,
                    HttpMethod.GET,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().orders()).hasSize(1)
            );
        }

        @DisplayName("주문 내역이 없으면 빈 목록을 반환한다.")
        @Test
        void getOrders_empty() {
            // arrange
            HttpEntity<Void> httpEntity = createHttpEntity(null, "testuser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderListResponse>> response = testRestTemplate.exchange(
                    ORDERS_ENDPOINT,
                    HttpMethod.GET,
                    httpEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().orders()).isEmpty()
            );
        }
    }

    @DisplayName("POST /api/v1/orders/{orderId}/cancel")
    @Nested
    class CancelOrder {

        @DisplayName("주문을 취소할 수 있다.")
        @Test
        void cancelOrder_success() {
            // arrange - 주문 생성
            OrderDto.CreateDirectRequest request = new OrderDto.CreateDirectRequest(testOption.getId(), 5);
            HttpEntity<OrderDto.CreateDirectRequest> orderEntity = createHttpEntity(request, "testuser", TEST_PASSWORD);
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> orderResponse = testRestTemplate.exchange(
                    ORDERS_ENDPOINT + "/direct", HttpMethod.POST, orderEntity, new ParameterizedTypeReference<>() {}
            );
            Long orderId = orderResponse.getBody().data().orderId();
            int stockAfterOrder = optionRepository.findById(testOption.getId()).orElseThrow().getStock();

            HttpEntity<Void> cancelEntity = createHttpEntity(null, "testuser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> response = testRestTemplate.exchange(
                    ORDERS_ENDPOINT + "/" + orderId + "/cancel",
                    HttpMethod.POST,
                    cancelEntity,
                    new ParameterizedTypeReference<>() {}
            );

            // assert
            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo(OrderStatus.CANCELED)
            );

            // 재고가 복구되었는지 확인
            int stockAfterCancel = optionRepository.findById(testOption.getId()).orElseThrow().getStock();
            assertThat(stockAfterCancel).isEqualTo(stockAfterOrder + 5);
        }

        @DisplayName("다른 사람의 주문을 취소하면 400 응답을 받는다.")
        @Test
        void cancelOrder_fail_not_owner() {
            // arrange - testuser로 주문 생성
            OrderDto.CreateDirectRequest request = new OrderDto.CreateDirectRequest(testOption.getId(), 2);
            HttpEntity<OrderDto.CreateDirectRequest> orderEntity = createHttpEntity(request, "testuser", TEST_PASSWORD);
            ResponseEntity<ApiResponse<OrderDto.OrderResponse>> orderResponse = testRestTemplate.exchange(
                    ORDERS_ENDPOINT + "/direct", HttpMethod.POST, orderEntity, new ParameterizedTypeReference<>() {}
            );
            Long orderId = orderResponse.getBody().data().orderId();

            // 다른 사용자 생성
            createTestMember("otheruser", TEST_PASSWORD);
            HttpEntity<Void> cancelEntity = createHttpEntity(null, "otheruser", TEST_PASSWORD);

            // act
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ORDERS_ENDPOINT + "/" + orderId + "/cancel",
                    HttpMethod.POST,
                    cancelEntity,
                    new ParameterizedTypeReference<>() {}
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
        member.addPoint(Money.of(100000000L));
        return memberRepository.save(member);
    }

    private <T> HttpEntity<T> createHttpEntity(T body, String memberId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Loopers-LoginId", memberId);
        headers.set("X-Loopers-LoginPw", password);
        return new HttpEntity<>(body, headers);
    }
}

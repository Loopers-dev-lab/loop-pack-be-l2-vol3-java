package com.loopers.interfaces.api.queue;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.product.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.domain.queue.EntryTokenRepository;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.user.UserJpaRepository;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.interfaces.api.user.UserV1Dto;
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
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class QueueV1ApiE2ETest {

    private static final String VALID_LOGIN_ID = "queueuser1";
    private static final String VALID_PASSWORD = "queue@1234";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String SIGNUP_ENDPOINT = "/api/v1/users/signup";
    private static final String ENDPOINT_CREATE_ORDER = "/api/v1/orders";
    private static final String ENDPOINT_GET_ORDERS = "/api/v1/orders";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private EntryTokenRepository entryTokenRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplateMaster;

    @AfterEach
    void tearDown() {
        for (long i = 1; i <= 10; i++) {
            redisTemplateMaster.delete("entry-token:" + i);
        }
        databaseCleanUp.truncateAllTables();
    }

    Long signUpAndGetUserId(String loginId, String password) {
        UserV1Dto.SignupRequest signupRequest = new UserV1Dto.SignupRequest(
                loginId, password, "대기열유저", "1990-01-01", loginId + "@test.com");
        testRestTemplate.exchange(
                SIGNUP_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(signupRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.UserResponse>>() {});
        return userJpaRepository.findByLoginId(loginId).orElseThrow().getId();
    }

    HttpHeaders createHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }

    @Nested
    @DisplayName("주문 API 입장 토큰 검증")
    class OrderTokenValidation {

        @Test
        @DisplayName("입장 토큰이 있는 유저는 주문에 성공한다")
        void 토큰_있는_유저_주문_성공() {
            // arrange
            Long userId = signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD);
            Brand brand = brandJpaRepository.save(new Brand("나이키"));
            Product product = productJpaRepository.save(
                    new Product(brand.getId(), "나이키 에어맥스", new Money(10000), new Stock(10)));
            entryTokenRepository.issueIfAbsent(userId, "test-token", 300L);
            OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(product.getId(), 1)), null);

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CREATE_ORDER, HttpMethod.POST,
                    new HttpEntity<>(request, createHeaders(VALID_LOGIN_ID, VALID_PASSWORD)),
                    new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        }

        @Test
        @DisplayName("입장 토큰이 없는 유저는 400 BAD_REQUEST를 반환한다")
        void 토큰_없는_유저_주문_거부() {
            // arrange
            signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD);
            OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(1L, 1)), null);

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CREATE_ORDER, HttpMethod.POST,
                    new HttpEntity<>(request, createHeaders(VALID_LOGIN_ID, VALID_PASSWORD)),
                    new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.BAD_REQUEST.getStatus());
        }

        @Test
        @DisplayName("입장 토큰이 만료된 유저는 400 BAD_REQUEST를 반환한다")
        void 토큰_만료_후_주문_거부() throws InterruptedException {
            // arrange
            Long userId = signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD);
            entryTokenRepository.issueIfAbsent(userId, "test-token", 1L); // TTL=1s
            Thread.sleep(1500);
            OrderV1Dto.OrderCreateRequest request = new OrderV1Dto.OrderCreateRequest(
                    List.of(new OrderV1Dto.OrderItemRequest(1L, 1)), null);

            // act
            ResponseEntity<ApiResponse<OrderV1Dto.OrderResponse>> response = testRestTemplate.exchange(
                    ENDPOINT_CREATE_ORDER, HttpMethod.POST,
                    new HttpEntity<>(request, createHeaders(VALID_LOGIN_ID, VALID_PASSWORD)),
                    new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode()).isEqualTo(ErrorType.BAD_REQUEST.getStatus());
        }

        @Test
        @DisplayName("GET 주문 조회는 입장 토큰 없이도 허용된다")
        void GET_주문조회는_토큰_불필요() {
            // arrange
            signUpAndGetUserId(VALID_LOGIN_ID, VALID_PASSWORD);

            // act
            ResponseEntity<ApiResponse<?>> response = testRestTemplate.exchange(
                    ENDPOINT_GET_ORDERS + "?startAt=2020-01-01&endAt=2099-12-31",
                    HttpMethod.GET,
                    new HttpEntity<>(createHeaders(VALID_LOGIN_ID, VALID_PASSWORD)),
                    new ParameterizedTypeReference<>() {});

            // assert
            assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        }
    }
}

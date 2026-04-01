package com.loopers.interfaces.api.queue.v1;

import static com.loopers.interfaces.api.order.v1.OrderSteps.createOrder;
import static com.loopers.interfaces.api.queue.v1.QueueSteps.enterQueue;
import static com.loopers.interfaces.api.queue.v1.QueueSteps.getPosition;
import static com.loopers.interfaces.api.user.v1.UserSteps.signUp;
import static com.loopers.support.E2ETestHelper.userAuthHeaders;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import com.loopers.application.queue.QueueAdmissionScheduler;
import com.loopers.interfaces.api.brand.v1.BrandDto;
import com.loopers.interfaces.api.brand.v1.BrandSteps;
import com.loopers.interfaces.api.order.v1.OrderDto;
import com.loopers.interfaces.api.product.v1.ProductDto;
import com.loopers.interfaces.api.product.v1.ProductSteps;
import com.loopers.interfaces.api.user.v1.UserV1Dto;
import com.loopers.support.BaseE2ETest;

@DisplayName("대기열 → 입장 토큰 발급 → 주문 E2E 테스트")
class QueueOrderFlowE2ETest extends BaseE2ETest {

    private static final String HEADER_ENTRY_TOKEN = "X-Entry-Token";

    @Autowired
    private QueueAdmissionScheduler scheduler;

    private HttpHeaders userHeaders;
    private Long productId;

    @BeforeEach
    void setUp() {
        var loginId = "flowuser1";
        var loginPw = "Password1!";
        signUp(testRestTemplate, new UserV1Dto.SignUpRequest(loginId, loginPw, "플로우유저", "1995-03-10", "flow@test.com"));
        userHeaders = userAuthHeaders(loginId, loginPw);

        var brandId = BrandSteps.createBrand(
                testRestTemplate,
                new BrandDto.CreateBrandRequest("플로우 브랜드", "https://example.com/logo.png", "브랜드 설명")
        );
        productId = ProductSteps.createProduct(
                testRestTemplate,
                new ProductDto.CreateProductRequest(brandId, "플로우 상품", "https://example.com/thumb.png", 10000L, 100L, "상품 설명")
        );
    }

    @DisplayName("대기열 진입 → 스케줄러 실행 → 주문 플로우")
    @Nested
    class QueueToOrder {

        @DisplayName("대기열 진입 후 스케줄러가 실행되면, 발급된 토큰으로 주문에 성공한다.")
        @Test
        void ordersSuccessfully_whenAdmittedByScheduler() {
            // arrange — 대기열 진입
            enterQueue(testRestTemplate, userHeaders);

            // act
            scheduler.admit();

            // 발급된 토큰 조회
            var positionResponse = getPosition(testRestTemplate, userHeaders);
            String entryToken = positionResponse.getBody().data().token();

            // 토큰을 포함하여 주문
            var orderHeaders = new HttpHeaders(userHeaders);
            orderHeaders.set(HEADER_ENTRY_TOKEN, entryToken);
            var orderRequest = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 1L)),
                    null
            );
            var orderResponse = createOrder(testRestTemplate, orderRequest, orderHeaders);

            // assert
            assertAll(
                    () -> assertThat(positionResponse.getBody().data().position()).isZero(),
                    () -> assertThat(entryToken).isNotNull(),
                    () -> assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED),
                    () -> assertThat(orderResponse.getBody().data().orderId()).isNotNull()
            );
        }

        @DisplayName("배치 크기(2명)를 초과하는 대기자 중, 입장하지 못한 유저는 임의 토큰으로 주문할 수 없다.")
        @Test
        void cannotOrder_whenNotAdmittedByScheduler() {
            // arrange — 3명 대기열 진입
            var user2Id = "flowuser2";
            var user2Pw = "Password2!";
            signUp(testRestTemplate, new UserV1Dto.SignUpRequest(user2Id, user2Pw, "유저2", "1996-01-01", "flow2@test.com"));
            var user2Headers = userAuthHeaders(user2Id, user2Pw);

            var user3Id = "flowuser3";
            var user3Pw = "Password3!";
            signUp(testRestTemplate, new UserV1Dto.SignUpRequest(user3Id, user3Pw, "유저3", "1997-01-01", "flow3@test.com"));
            var user3Headers = userAuthHeaders(user3Id, user3Pw);

            enterQueue(testRestTemplate, userHeaders);
            enterQueue(testRestTemplate, user2Headers);
            enterQueue(testRestTemplate, user3Headers);

            // act
            scheduler.admit();

            // user3은 아직 대기 중 → 임의 토큰으로 주문 시도
            var user3OrderHeaders = new HttpHeaders(user3Headers);
            user3OrderHeaders.set(HEADER_ENTRY_TOKEN, UUID.randomUUID().toString());
            var orderRequest = new OrderDto.CreateOrderRequest(
                    List.of(new OrderDto.OrderItemRequest(productId, 1L)),
                    null
            );
            var orderResponse = createOrder(testRestTemplate, orderRequest, user3OrderHeaders);

            // assert — user3의 대기열 순번 확인
            var user3Position = getPosition(testRestTemplate, user3Headers);
            assertAll(
                    () -> assertThat(user3Position.getBody().data().position()).isEqualTo(1),
                    () -> assertThat(user3Position.getBody().data().token()).isNull(),
                    () -> assertThat(orderResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN)
            );
        }
    }
}

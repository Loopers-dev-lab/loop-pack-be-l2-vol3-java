package com.loopers.application.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.common.Money;
import com.loopers.domain.order.OrderCreatedEvent;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.product.Product;
import com.loopers.domain.stock.Stock;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.infrastructure.stock.StockJpaRepository;
import com.loopers.interfaces.api.order.OrderV1Dto;
import com.loopers.utils.DatabaseCleanUp;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

@RecordApplicationEvents
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderCreatedEventTest {

    private static final String ENDPOINT = "/api/v1/orders";
    private static final String USERS_ENDPOINT = "/api/v1/users";
    private static final String LOGIN_ID = "testuser";
    private static final String PASSWORD = "Test1234!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private ApplicationEvents applicationEvents;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private StockJpaRepository stockJpaRepository;

    @BeforeEach
    void setUp() {
        testRestTemplate.exchange(
            USERS_ENDPOINT, HttpMethod.POST,
            new HttpEntity<>(java.util.Map.of(
                "loginId", LOGIN_ID, "password", PASSWORD,
                "name", "홍길동", "birthDate", "19900101", "email", "test@example.com"
            )),
            new ParameterizedTypeReference<>() {}
        );
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("카드 정보와 함께 주문을 생성하면, OrderCreatedEvent가 올바른 정보로 발행된다.")
    @Test
    void publishesOrderCreatedEvent_whenOrderIsCreatedWithCardInfo() {
        // arrange
        Brand brand = brandJpaRepository.save(new Brand("나이키"));
        Product product = productJpaRepository.save(new Product(brand.getId(), "신발", new Money(50000L), "설명"));
        stockJpaRepository.save(new Stock(product.getId(), 100L));

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", LOGIN_ID);
        headers.set("X-Loopers-LoginPw", PASSWORD);
        headers.set("Content-Type", "application/json");

        java.util.Map<String, Object> request = java.util.Map.of(
            "items", List.of(java.util.Map.of("productId", product.getId(), "quantity", 2)),
            "cardType", "SAMSUNG",
            "cardNo", "1234-5678-9012-3456",
            "updateDefaultCard", false
        );

        // act
        testRestTemplate.exchange(
            ENDPOINT, HttpMethod.POST,
            new HttpEntity<>(request, headers),
            new ParameterizedTypeReference<>() {}
        );

        // assert
        assertThat(applicationEvents.stream(OrderCreatedEvent.class))
            .hasSize(1)
            .first()
            .satisfies(event -> assertAll(
                () -> assertThat(event.cardType()).isEqualTo(CardType.SAMSUNG),
                () -> assertThat(event.cardNo()).isEqualTo("1234-5678-9012-3456"),
                () -> assertThat(event.amount()).isEqualTo(100000L)
            ));
    }
}
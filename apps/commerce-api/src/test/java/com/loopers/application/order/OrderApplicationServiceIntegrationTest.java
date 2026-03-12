package com.loopers.application.order;

import com.loopers.application.order.query.OrderAccessRequest;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class OrderApplicationServiceIntegrationTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("OrderApplicationService 통합: 주문 생성 후 조회 가능")
    void createAndGet() {
        UUID productId = UUID.randomUUID();
        OrderItem item = new OrderItem(productId, 1, "상품명", 12000, "브랜드명");
        Order created = orderApplicationService.create("ordermember", List.of(item), null);

        Order found = orderApplicationService.getById(new OrderAccessRequest(created.id(), "ordermember", false));

        assertThat(found.id()).isEqualTo(created.id());
    }
}

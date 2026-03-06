package com.loopers.application.order;

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
class OrderQueryApplicationServiceIntegrationTest {

    @Autowired
    private OrderApplicationService orderApplicationService;

    @Autowired
    private OrderQueryApplicationService orderQueryApplicationService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("OrderQueryApplicationService 통합: 주문 아이템 productId 존재 여부 조회")
    void existsOrderItemByProductId() {
        UUID productId = UUID.randomUUID();
        OrderItem item = new OrderItem(productId, 1, "상품명", 12000, "브랜드명");
        orderApplicationService.create("orderquerymember", List.of(item), null);

        boolean exists = orderQueryApplicationService.existsOrderItemByProductId(productId);

        assertThat(exists).isTrue();
    }
}

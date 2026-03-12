package com.loopers.interfaces.api.admin;

import com.loopers.application.order.OrderUseCase;
import com.loopers.domain.order.Order;
import com.loopers.domain.order.OrderItem;
import com.loopers.domain.order.OrderRepository;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ImportTestcontainers(MySqlTestContainersConfig.class)
@ActiveProfiles("test")
class AdminOrderControllerTest {

    private static final String ADMIN_LDAP_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_LDAP_VALUE = "loopers.admin";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private OrderUseCase orderUseCase;

    @Autowired
    private OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("관리자 주문 취소는 OrderUseCase.cancel을 호출한다")
    void cancelOrder_callsOrderUseCase() throws Exception {
        UUID productId = UUID.fromString("00000000-0000-0000-0000-000000000999");

        Order ordered = new Order(
                "member-1",
                "ORDER-1",
                List.of(new OrderItem(productId, 1, "사료", 10000, "브랜드A")),
                null
        );
        UUID orderId = orderRepository.save(ordered).id();

        mockMvc.perform(patch("/api-admin/v1/orders/{orderId}/cancel", orderId)
                        .header(ADMIN_LDAP_HEADER, ADMIN_LDAP_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(orderId.toString()))
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("관리자 주문 취소는 LDAP 헤더가 없으면 401")
    void cancelOrder_withoutLdapHeader_returnsUnauthorized() throws Exception {
        UUID orderId = UUID.fromString("00000000-0000-0000-0000-000000000123");

        mockMvc.perform(patch("/api-admin/v1/orders/{orderId}/cancel", orderId))
                .andExpect(status().isUnauthorized());
    }
}

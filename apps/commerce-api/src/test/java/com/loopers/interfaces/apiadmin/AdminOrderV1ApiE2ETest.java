package com.loopers.interfaces.apiadmin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.order.OrderItemModel;
import com.loopers.domain.order.OrderModel;
import com.loopers.infrastructure.order.OrderItemJpaRepository;
import com.loopers.infrastructure.order.OrderJpaRepository;
import com.loopers.support.enums.OrderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("Admin Order API V1 E2E 테스트")
class AdminOrderV1ApiE2ETest {

    private static final String ADMIN_HEADER = "X-Loopers-Ldap";
    private static final String ADMIN_VALUE = "loopers.admin";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired OrderJpaRepository orderJpaRepository;
    @Autowired OrderItemJpaRepository orderItemJpaRepository;

    @Test
    @DisplayName("관리자가 모든 사용자의 주문을 조회할 수 있다")
    void GET_orders_ShouldReturn200WithAllOrders() throws Exception {
        orderJpaRepository.save(OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(10000)));
        orderJpaRepository.save(OrderModel.create(2L, OrderType.CART, BigDecimal.valueOf(20000)));

        mockMvc.perform(get("/api-admin/v1/orders")
                        .header(ADMIN_HEADER, ADMIN_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("주문 상세에 스냅샷이 포함된다")
    void GET_orderDetail_ShouldReturn200WithSnapshots() throws Exception {
        OrderModel order = orderJpaRepository.save(
                OrderModel.create(1L, OrderType.DIRECT, BigDecimal.valueOf(30000)));
        orderItemJpaRepository.save(OrderItemModel.create(
                order.getOrderId(), 1, 1L, 1L, 2,
                "테스트상품", BigDecimal.valueOf(15000), "b1", "테스트브랜드", null));

        mockMvc.perform(get("/api-admin/v1/orders/" + order.getOrderId())
                        .header(ADMIN_HEADER, ADMIN_VALUE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderId").value(order.getOrderId()))
                .andExpect(jsonPath("$.data.items[0].snapshotProductName").value("테스트상품"));
    }
}

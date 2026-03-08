package com.loopers.interfaces.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.application.user.UserFacade;
import com.loopers.application.user.UserDto;
import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandDescription;
import com.loopers.domain.brand.BrandName;
import com.loopers.domain.user.Gender;
import com.loopers.infrastructure.brand.BrandJpaRepository;
import com.loopers.infrastructure.product.ProductJpaRepository;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class OrderE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserFacade userFacade;

    @Autowired
    private BrandJpaRepository brandJpaRepository;

    @Autowired
    private ProductJpaRepository productJpaRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    private Long userId;
    private Long productId;

    @BeforeEach
    void setUp() {
        UserDto.UserInfo user = userFacade.register(
            "orderapi1",
            "TestPass1!",
            "오더사용자",
            LocalDate.of(2000, 1, 1),
            "order-api@loopers.com",
            Gender.MALE
        );
        userId = user.id();

        Brand brand = brandJpaRepository.save(
            Brand.create(new BrandName("ORDER_API"), new BrandDescription("오더 API 브랜드"))
        );

        productId = productJpaRepository.save(
            com.loopers.domain.product.Product.create(
                brand.getId(),
                "주문 API 상품",
                "설명",
                BigDecimal.valueOf(12000),
                10
            )
        ).getId();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
    }

    @DisplayName("POST /api/v1/orders: 주문 생성에 성공한다")
    @Test
    void placeOrder_success() throws Exception {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", productId);
        item.put("quantity", 2);
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(item));

        mockMvc.perform(post("/api/v1/orders")
                .header("X-Loopers-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.userId").value(userId))
            .andExpect(jsonPath("$.data.items[0].productId").value(productId))
            .andExpect(jsonPath("$.data.totalAmount").value(24000));
    }

    @DisplayName("DELETE /api/v1/orders/{orderId}: 주문 취소에 성공한다")
    @Test
    void cancelOrder_success() throws Exception {
        Map<String, Object> item = new HashMap<>();
        item.put("productId", productId);
        item.put("quantity", 1);
        Map<String, Object> body = new HashMap<>();
        body.put("items", List.of(item));

        String created = mockMvc.perform(post("/api/v1/orders")
                .header("X-Loopers-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Long orderId = objectMapper.readTree(created).get("data").get("id").asLong();

        mockMvc.perform(delete("/api/v1/orders/" + orderId)
                .header("X-Loopers-User-Id", userId))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}

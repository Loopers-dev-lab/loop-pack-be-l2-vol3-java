package com.loopers.interfaces.api.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.context.ImportTestcontainers;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "loopers.queue.order.enabled=false"
})
@AutoConfigureMockMvc
@ImportTestcontainers({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
@ActiveProfiles("test")
class OrderQueueDisabledFlowTest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";
    private static final String TEST_LOGIN_ID = "disableduser";
    private static final String TEST_PASSWORD = "Test1234!@";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() throws Exception {
        var registerRequest = new com.loopers.interfaces.api.member.MemberDto.RegisterRequest(
                TEST_LOGIN_ID, TEST_PASSWORD, "비활성", "19900101", "disabled@test.com", "010-1111-2222"
        );
        mockMvc.perform(post("/api/v1/members")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)));
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @Test
    @DisplayName("대기열이 비활성화되면 토큰 없이도 기존 주문 검증 흐름으로 진입한다")
    void createOrderWithoutTokenWhenQueueDisabledUsesOriginalFlow() throws Exception {
        OrderDto.CreateOrderRequest request = new OrderDto.CreateOrderRequest(
                List.of(new OrderDto.OrderItemRequest(UUID.randomUUID(), 1))
        );

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
                        .header(HEADER_LOGIN_ID, TEST_LOGIN_ID)
                        .header(HEADER_LOGIN_PW, TEST_PASSWORD))
                .andExpect(status().isNotFound());
    }
}

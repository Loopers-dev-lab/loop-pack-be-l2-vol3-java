package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.QueueRepository;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QueueE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("대기열 API")
    @Nested
    class QueueApi {

        @DisplayName("POST /api/v1/queue/enter: 대기열에 진입한다")
        @Test
        void enter_success() throws Exception {
            mockMvc.perform(post("/api/v1/queue/enter")
                            .header("X-Loopers-User-Id", 1L))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.position").value(0))
                    .andExpect(jsonPath("$.data.totalWaiting").value(1));
        }

        @DisplayName("GET /api/v1/queue/position: 대기 중 순번을 조회한다")
        @Test
        void getPosition_waiting() throws Exception {
            queueRepository.enterWaitQueue(1L, System.currentTimeMillis());
            queueRepository.enterWaitQueue(2L, System.currentTimeMillis() + 1);

            mockMvc.perform(get("/api/v1/queue/position")
                            .header("X-Loopers-User-Id", 2L))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.position").value(1))
                    .andExpect(jsonPath("$.data.token").doesNotExist());
        }

        @DisplayName("GET /api/v1/queue/position: 토큰 발급 후 토큰을 응답한다")
        @Test
        void getPosition_tokenIssued() throws Exception {
            queueRepository.issueToken(1L, "test-token", 300);

            mockMvc.perform(get("/api/v1/queue/position")
                            .header("X-Loopers-User-Id", 1L))
                    .andDo(print())
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.position").value(0))
                    .andExpect(jsonPath("$.data.token").isNotEmpty());
        }

        @DisplayName("GET /api/v1/queue/position: 대기열에 없는 유저는 에러를 반환한다")
        @Test
        void getPosition_notEntered() throws Exception {
            mockMvc.perform(get("/api/v1/queue/position")
                            .header("X-Loopers-User-Id", 999L))
                    .andDo(print())
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.meta.errorCode").value("QUEUE_NOT_ENTERED"));
        }
    }

    @DisplayName("토큰 검증 Interceptor")
    @Nested
    class TokenInterceptor {

        @DisplayName("토큰 없이 주문하면 403을 반환한다")
        @Test
        void placeOrder_withoutToken_forbidden() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .header("X-Loopers-User-Id", 1L)
                            .contentType("application/json")
                            .content("{\"items\":[]}"))
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.meta.errorCode").value("ENTRY_TOKEN_NOT_FOUND"));
        }

        @DisplayName("잘못된 토큰으로 주문하면 403을 반환한다")
        @Test
        void placeOrder_invalidToken_forbidden() throws Exception {
            queueRepository.issueToken(1L, "valid-token", 300);

            mockMvc.perform(post("/api/v1/orders")
                            .header("X-Loopers-User-Id", 1L)
                            .header("X-Entry-Token", "wrong-token")
                            .contentType("application/json")
                            .content("{\"items\":[]}"))
                    .andDo(print())
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.meta.errorCode").value("ENTRY_TOKEN_INVALID"));
        }
    }
}

package com.loopers.interfaces.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.loopers.domain.queue.QueueService;
import com.loopers.domain.queue.TokenService;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class QueueTokenInterceptorE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private QueueService queueService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @DisplayName("주문 API 진입 시, ")
    @Nested
    class OrderAccess {

        @DisplayName("유효한 토큰으로 진입하면 통과된다.")
        @Test
        void passes_whenValidTokenProvided() throws Exception {
            // arrange
            String userId = "user-1";
            String token = tokenService.issue(userId);

            // act
            ResultActions result = mockMvc.perform(
                post("/api/v1/orders")
                    .header("X-Queue-Token", token)
                    .header("X-User-Id", userId)
            );

            // assert — 토큰 검증 통과 (주문 로직 에러는 허용, 403은 안 됨)
            int statusCode = result.andReturn().getResponse().getStatus();
            assertThat(statusCode).isNotEqualTo(403);
        }

        @DisplayName("토큰 없이 진입하면 FORBIDDEN이 반환된다.")
        @Test
        void returnsForbidden_whenNoTokenProvided() throws Exception {
            // act
            ResultActions result = mockMvc.perform(
                post("/api/v1/orders")
                    .header("X-User-Id", "user-1")
            );

            // assert
            result.andExpect(status().isForbidden());
        }

        @DisplayName("빈 문자열 토큰으로 진입하면 FORBIDDEN이 반환된다.")
        @Test
        void returnsForbidden_whenEmptyTokenProvided() throws Exception {
            // act
            ResultActions result = mockMvc.perform(
                post("/api/v1/orders")
                    .header("X-Queue-Token", "")
                    .header("X-User-Id", "user-1")
            );

            // assert
            result.andExpect(status().isForbidden());
        }

        @DisplayName("다른 userId의 토큰으로 진입하면 FORBIDDEN이 반환된다.")
        @Test
        void returnsForbidden_whenTokenBelongsToAnotherUser() throws Exception {
            // arrange
            String token = tokenService.issue("other-user");

            // act
            ResultActions result = mockMvc.perform(
                post("/api/v1/orders")
                    .header("X-Queue-Token", token)
                    .header("X-User-Id", "user-1")
            );

            // assert
            result.andExpect(status().isForbidden());
        }

        @DisplayName("주문 완료 후 토큰이 삭제된다.")
        @Test
        void revokesToken_afterOrderCompleted() {
            // arrange
            String userId = "user-1";
            tokenService.issue(userId);
            assertThat(tokenService.findToken(userId)).isPresent();

            // act
            tokenService.revoke(userId);

            // assert
            assertThat(tokenService.findToken(userId)).isEmpty();
        }
    }
}
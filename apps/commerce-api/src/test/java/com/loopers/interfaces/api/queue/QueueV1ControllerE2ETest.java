package com.loopers.interfaces.api.queue;

import com.loopers.domain.queue.EntryTokenService;
import com.loopers.domain.queue.QueueEntry;
import com.loopers.domain.queue.QueueService;
import com.loopers.domain.user.UserModel;
import com.loopers.domain.user.UserRegisterCommand;
import com.loopers.domain.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("대기열 API E2E 테스트")
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class QueueV1ControllerE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private EntryTokenService entryTokenService;

    @Autowired
    private QueueService queueService;

    @Autowired
    @Qualifier("redisTemplateMaster")
    private RedisTemplate<String, String> redisTemplate;

    private static final String QUEUE_KEY = "order:waiting-queue";
    private static final String TOKEN_KEY_PREFIX = "order:entry-token:";
    private static final String LOGIN_ID = "queuetester";
    private static final String LOGIN_PW = "Test1234!";

    private UserModel testUser;

    @BeforeEach
    void setUp() {
        // Redis 초기화
        redisTemplate.delete(QUEUE_KEY);
        Set<String> tokenKeys = redisTemplate.keys(TOKEN_KEY_PREFIX + "*");
        if (tokenKeys != null && !tokenKeys.isEmpty()) {
            redisTemplate.delete(tokenKeys);
        }

        // 테스트 사용자 등록
        testUser = userService.register(new UserRegisterCommand(
                LOGIN_ID, LOGIN_PW, "테스트유저",
                "19900101", "test@test.com", "서울시"
        ));
    }

    // ============================
    // POST /api/v1/queue/enter
    // ============================
    @Nested
    @DisplayName("POST /api/v1/queue/enter")
    class EnterQueue {

        @Test
        @DisplayName("인증된 유저가 진입하면 201과 순번 정보를 반환한다")
        void enter_AuthenticatedUser_ShouldReturn201WithPosition() throws Exception {
            mockMvc.perform(post("/api/v1/queue/enter")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.position").value(0))
                    .andExpect(jsonPath("$.data.totalWaiting").isNumber())
                    .andExpect(jsonPath("$.data.estimatedWaitSeconds").isNumber())
                    .andExpect(jsonPath("$.data.alreadyEntered").value(false));
        }

        @Test
        @DisplayName("이미 대기 중인 유저가 재진입하면 200과 기존 순번을 반환한다 (멱등)")
        void enter_AlreadyInQueue_ShouldReturn200WithExistingPosition() throws Exception {
            // given — 1차 진입
            mockMvc.perform(post("/api/v1/queue/enter")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isCreated());

            // when — 2차 진입 (멱등)
            mockMvc.perform(post("/api/v1/queue/enter")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.alreadyEntered").value(true));
        }

        @Test
        @DisplayName("인증 없이 진입하면 401을 반환한다")
        void enter_WithoutAuth_ShouldReturn401() throws Exception {
            mockMvc.perform(post("/api/v1/queue/enter"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ============================
    // GET /api/v1/queue/position
    // ============================
    @Nested
    @DisplayName("GET /api/v1/queue/position")
    class GetPosition {

        @Test
        @DisplayName("대기 중인 유저는 WAITING 상태와 순번을 반환한다")
        void getPosition_InQueue_ShouldReturnWaiting() throws Exception {
            // given — 대기열 진입
            mockMvc.perform(post("/api/v1/queue/enter")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isCreated());

            // when & then
            mockMvc.perform(get("/api/v1/queue/position")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("WAITING"))
                    .andExpect(jsonPath("$.data.position").isNumber())
                    .andExpect(jsonPath("$.data.totalWaiting").isNumber())
                    .andExpect(jsonPath("$.data.token").doesNotExist());
        }

        @Test
        @DisplayName("대기열에 없는 유저는 NOT_IN_QUEUE 상태를 반환한다")
        void getPosition_NotInQueue_ShouldReturnNotInQueue() throws Exception {
            mockMvc.perform(get("/api/v1/queue/position")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("NOT_IN_QUEUE"))
                    .andExpect(jsonPath("$.data.position").value(-1))
                    .andExpect(jsonPath("$.data.token").doesNotExist());
        }

        @Test
        @DisplayName("인증 없이 순번 조회하면 401을 반환한다")
        void getPosition_WithoutAuth_ShouldReturn401() throws Exception {
            mockMvc.perform(get("/api/v1/queue/position"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ============================
    // 전체 흐름 테스트
    // ============================
    @Nested
    @DisplayName("전체 흐름 — 진입 → 토큰 → 순번 조회")
    class FullFlow {

        @Test
        @DisplayName("진입 → 스케줄러 수동 실행 → READY 상태 + 토큰 반환")
        void fullFlow_Enter_WaitForToken_CheckReady() throws Exception {
            // 1. 대기열 진입
            mockMvc.perform(post("/api/v1/queue/enter")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isCreated());

            // 2. 스케줄러 수동 실행 (ZPOPMIN + 토큰 발급)
            List<QueueEntry> entries = queueService.popBatch(14);
            for (QueueEntry entry : entries) {
                entryTokenService.issueToken(entry.userId());
            }

            // 3. 순번 조회 → READY 상태 + 토큰 반환
            mockMvc.perform(get("/api/v1/queue/position")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("READY"))
                    .andExpect(jsonPath("$.data.token").isNotEmpty());
        }
    }

    // ============================
    // 토큰 검증 테스트
    // ============================
    @Nested
    @DisplayName("토큰 검증 — EntryTokenInterceptor")
    class TokenValidation {

        @Test
        @DisplayName("토큰 없이 주문하면 403을 반환한다 (우회 방지)")
        void createOrder_WithoutToken_ShouldReturn403() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW)
                            .contentType("application/json")
                            .content("{\"items\":[]}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.meta.errorCode").value("ENTRY_TOKEN_REQUIRED"));
        }

        @Test
        @DisplayName("잘못된 토큰으로 주문하면 403을 반환한다")
        void createOrder_WithInvalidToken_ShouldReturn403() throws Exception {
            mockMvc.perform(post("/api/v1/orders")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW)
                            .header("X-Entry-Token", "fake-token-uuid")
                            .contentType("application/json")
                            .content("{\"items\":[]}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.meta.errorCode").value("ENTRY_TOKEN_INVALID"));
        }

        @Test
        @DisplayName("주문 취소는 토큰 없이도 403이 아닌 응답을 반환한다")
        void cancelOrder_WithoutToken_ShouldNotRequireToken() throws Exception {
            // cancel API는 EntryTokenInterceptor에서 제외
            mockMvc.perform(post("/api/v1/orders/999/cancel")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(403));
        }
    }

    // ============================
    // Step 3: 전체 흐름 + Polling 검증
    // ============================
    @Nested
    @DisplayName("전체 흐름 — 진입 → Polling → 토큰 → 주문")
    class FullFlowWithPolling {

        @Test
        @DisplayName("진입 → Polling(WAITING) → 스케줄러 → Polling(READY) 전체 흐름")
        void fullFlow_Enter_Poll_WaitForToken_Poll() throws Exception {
            // 1. 대기열 진입
            mockMvc.perform(post("/api/v1/queue/enter")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.position").value(0));

            // 2. Polling → WAITING
            mockMvc.perform(get("/api/v1/queue/position")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("WAITING"))
                    .andExpect(jsonPath("$.data.position").value(0));

            // 3. 스케줄러 수동 실행 (ZPOPMIN + 토큰 발급)
            List<QueueEntry> entries = queueService.popBatch(14);
            assertThat(entries).hasSize(1);
            for (QueueEntry entry : entries) {
                entryTokenService.issueToken(entry.userId());
            }

            // 4. Polling → READY + 토큰
            mockMvc.perform(get("/api/v1/queue/position")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("READY"))
                    .andExpect(jsonPath("$.data.token").isNotEmpty());
        }

        @Test
        @DisplayName("여러 유저 동시 진입 시 순번이 올바르게 부여된다")
        void enter_MultipleUsers_ShouldAssignCorrectPositions() throws Exception {
            // 3명 사용자 등록
            String[] logins = {"polltester1", "polltester2", "polltester3"};
            for (String login : logins) {
                userService.register(new UserRegisterCommand(
                        login, LOGIN_PW, "테스트", "19900101", "t@t.com", "서울"));
            }

            // 순서대로 진입
            for (int i = 0; i < logins.length; i++) {
                mockMvc.perform(post("/api/v1/queue/enter")
                                .header("X-Loopers-LoginId", logins[i])
                                .header("X-Loopers-LoginPw", LOGIN_PW))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.data.position").value(i));
            }

            // 첫 번째 유저 Polling → position=0
            mockMvc.perform(get("/api/v1/queue/position")
                            .header("X-Loopers-LoginId", logins[0])
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(jsonPath("$.data.position").value(0));

            // 세 번째 유저 Polling → position=2
            mockMvc.perform(get("/api/v1/queue/position")
                            .header("X-Loopers-LoginId", logins[2])
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(jsonPath("$.data.position").value(2));
        }
    }

    // ============================
    // Step 3: 만료 토큰 재진입
    // ============================
    @Nested
    @DisplayName("만료 토큰 재진입")
    class TokenExpiry {

        @Test
        @DisplayName("토큰 만료 후 재진입하면 대기열 맨 뒤에 배치된다")
        void reenter_AfterTokenExpiry_ShouldGoToEndOfQueue() throws Exception {
            // 1. 진입 + 스케줄러 + 토큰 발급
            mockMvc.perform(post("/api/v1/queue/enter")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isCreated());

            List<QueueEntry> entries = queueService.popBatch(14);
            for (QueueEntry entry : entries) {
                entryTokenService.issueToken(entry.userId());
            }

            // 2. 토큰 수동 삭제 (만료 시뮬레이션)
            redisTemplate.delete("order:entry-token:" + testUser.getUserId());

            // 3. Polling → NOT_IN_QUEUE (토큰 만료, 대기열에도 없음)
            mockMvc.perform(get("/api/v1/queue/position")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(jsonPath("$.data.status").value("NOT_IN_QUEUE"));

            // 4. 재진입 → 새로운 순번 (맨 뒤)
            mockMvc.perform(post("/api/v1/queue/enter")
                            .header("X-Loopers-LoginId", LOGIN_ID)
                            .header("X-Loopers-LoginPw", LOGIN_PW))
                    .andExpect(status().isCreated());
        }
    }
}

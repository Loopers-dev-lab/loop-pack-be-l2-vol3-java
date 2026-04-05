package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.QueueFacade;
import com.loopers.config.WebMvcConfig;
import com.loopers.domain.auth.LdapAuthService;
import com.loopers.domain.member.Member;
import com.loopers.domain.member.MemberService;
import com.loopers.domain.queue.QueuePositionInfo;
import com.loopers.interfaces.api.auth.AdminAuthInterceptor;
import com.loopers.interfaces.api.auth.LoginAdminArgumentResolver;
import com.loopers.interfaces.api.auth.LoginMemberArgumentResolver;
import com.loopers.interfaces.api.auth.MemberAuthInterceptor;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// [단위 테스트 - Interfaces Layer]
//
// 테스트 대상: QueueV1Controller
// 테스트 유형: 단위 테스트 (MockBean: QueueFacade, MemberService, LdapAuthService + MockMvc)
// 테스트 범위: HTTP 요청/응답 검증
//
// POST /api/v1/queue/enter:
//   - 인증된 사용자 진입 시 순번 반환 (200 OK)
//   - 인증 없이 진입 시 401 Unauthorized
//   - 대기열 비활성 상태 진입 시 400 Bad Request
//
// GET /api/v1/queue/position:
//   - 대기 중 유저: position, estimatedWaitSeconds, pollIntervalSeconds 반환
//   - 토큰 발급 유저: position=0, token 포함 응답
//   - 대기열 미존재 유저: 404 Not Found
//   - 인증 없이 조회 시 401, 비활성 상태 시 400
@WebMvcTest(QueueV1Controller.class)
@Import({WebMvcConfig.class, MemberAuthInterceptor.class, LoginMemberArgumentResolver.class,
        AdminAuthInterceptor.class, LoginAdminArgumentResolver.class})
@DisplayName("QueueV1Controller 단위 테스트")
class QueueV1ControllerTest {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QueueFacade queueFacade;

    @MockBean
    private MemberService memberService;

    @MockBean
    private LdapAuthService ldapAuthService;

    private Member mockAuthenticatedMember() {
        Member member = mock(Member.class);
        when(member.getId()).thenReturn(1L);
        when(memberService.authenticate("testuser1", "Password1!")).thenReturn(member);
        return member;
    }

    @Nested
    @DisplayName("POST /api/v1/queue/enter")
    class Enter {

        @Test
        @DisplayName("인증된 사용자가 대기열에 진입하면 순번을 반환한다")
        void enter_success() throws Exception {
            // given
            Member member = mockAuthenticatedMember();
            when(queueFacade.enter(1L)).thenReturn(128L);

            // when & then
            mockMvc.perform(post("/api/v1/queue/enter")
                            .header(HEADER_LOGIN_ID, "testuser1")
                            .header(HEADER_LOGIN_PW, "Password1!")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.position").value(128));
        }

        @Test
        @DisplayName("인증 없이 대기열에 진입하면 401을 반환한다")
        void enter_without_auth_returns_401() throws Exception {
            // given & when & then
            mockMvc.perform(post("/api/v1/queue/enter")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("대기열 비활성 상태에서 진입하면 400을 반환한다")
        void enter_when_queue_disabled_returns_400() throws Exception {
            // given
            mockAuthenticatedMember();
            when(queueFacade.enter(1L))
                    .thenThrow(new CoreException(ErrorType.BAD_REQUEST, "대기열이 비활성 상태입니다."));

            // when & then
            mockMvc.perform(post("/api/v1/queue/enter")
                            .header(HEADER_LOGIN_ID, "testuser1")
                            .header(HEADER_LOGIN_PW, "Password1!")
                            .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.meta.result").value("FAIL"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/queue/position")
    class GetPosition {

        @Test
        @DisplayName("대기 중인 유저의 순번, 예상 대기 시간, polling 주기를 반환한다")
        void getPosition_waiting() throws Exception {
            // given
            mockAuthenticatedMember();
            QueuePositionInfo info = QueuePositionInfo.waiting(127, 18, 100);
            when(queueFacade.getPosition(1L)).thenReturn(info);

            // when & then
            mockMvc.perform(get("/api/v1/queue/position")
                            .header(HEADER_LOGIN_ID, "testuser1")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.meta.result").value("SUCCESS"))
                    .andExpect(jsonPath("$.data.position").value(128))
                    .andExpect(jsonPath("$.data.estimatedWaitSeconds").value(info.estimatedWaitSeconds()))
                    .andExpect(jsonPath("$.data.token").isEmpty())
                    .andExpect(jsonPath("$.data.pollIntervalSeconds").value(3));
        }

        @Test
        @DisplayName("토큰이 발급된 유저는 position=0과 토큰을 반환한다")
        void getPosition_ready() throws Exception {
            // given
            mockAuthenticatedMember();
            QueuePositionInfo info = QueuePositionInfo.ready("abc-token-123");
            when(queueFacade.getPosition(1L)).thenReturn(info);

            // when & then
            mockMvc.perform(get("/api/v1/queue/position")
                            .header(HEADER_LOGIN_ID, "testuser1")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.position").value(0))
                    .andExpect(jsonPath("$.data.estimatedWaitSeconds").value(0))
                    .andExpect(jsonPath("$.data.token").value("abc-token-123"))
                    .andExpect(jsonPath("$.data.pollIntervalSeconds").value(0));
        }

        @Test
        @DisplayName("대기열에 없는 유저가 조회하면 404를 반환한다")
        void getPosition_not_in_queue_returns_404() throws Exception {
            // given
            mockAuthenticatedMember();
            when(queueFacade.getPosition(1L))
                    .thenThrow(new CoreException(ErrorType.NOT_FOUND, "대기열에 존재하지 않는 유저입니다."));

            // when & then
            mockMvc.perform(get("/api/v1/queue/position")
                            .header(HEADER_LOGIN_ID, "testuser1")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.meta.result").value("FAIL"));
        }

        @Test
        @DisplayName("인증 없이 순번을 조회하면 401을 반환한다")
        void getPosition_without_auth_returns_401() throws Exception {
            // given & when & then
            mockMvc.perform(get("/api/v1/queue/position"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("대기열 비활성 상태에서 순번을 조회하면 400을 반환한다")
        void getPosition_when_queue_disabled_returns_400() throws Exception {
            // given
            mockAuthenticatedMember();
            when(queueFacade.getPosition(1L))
                    .thenThrow(new CoreException(ErrorType.BAD_REQUEST, "대기열이 비활성 상태입니다."));

            // when & then
            mockMvc.perform(get("/api/v1/queue/position")
                            .header(HEADER_LOGIN_ID, "testuser1")
                            .header(HEADER_LOGIN_PW, "Password1!"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.meta.result").value("FAIL"));
        }
    }
}

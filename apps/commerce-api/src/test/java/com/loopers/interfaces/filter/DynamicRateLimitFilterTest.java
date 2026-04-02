package com.loopers.interfaces.filter;

import com.loopers.application.queue.QueueApp;
import com.loopers.application.queue.RateLimitModeEvaluator;
import com.loopers.config.RateLimitProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@DisplayName("DynamicRateLimitFilter 단위 테스트")
class DynamicRateLimitFilterTest {

    private DynamicRateLimitFilter filter;
    private RateLimitModeEvaluator evaluator;
    private QueueApp queueApp;

    @BeforeEach
    void setUp() {
        evaluator = mock(RateLimitModeEvaluator.class);
        queueApp = mock(QueueApp.class);
        RateLimitProperties properties = new RateLimitProperties(
                true, 0.85, 0.60, 10, 1000, 3, 10, 1, 2, 3, 10
        );
        filter = new DynamicRateLimitFilter(evaluator, properties, queueApp);
    }

    @Test
    @DisplayName("rate limit 비활성 상태에서는 모든 요청 통과")
    void inactive_allRequestsPass() throws Exception {
        when(evaluator.isActive()).thenReturn(false);

        MockHttpServletRequest request = createQueueRequest("1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("rate limit 활성 + 임계값 미만 → 통과")
    void active_belowLimit_passes() throws Exception {
        when(evaluator.isActive()).thenReturn(true);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = createQueueRequest("1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("rate limit 활성 + 임계값 초과 → 429")
    void active_aboveLimit_returns429() throws Exception {
        when(evaluator.isActive()).thenReturn(true);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = createQueueRequest("1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
        }

        MockHttpServletRequest request = createQueueRequest("1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).isEqualTo(429);
    }

    @Test
    @DisplayName("서로 다른 userId는 독립적 카운터")
    void differentUsers_independentCounters() throws Exception {
        when(evaluator.isActive()).thenReturn(true);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = createQueueRequest("1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
        }

        MockHttpServletRequest request = createQueueRequest("2");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        assertThat(response.getStatus()).as("다른 userId는 별도 카운터").isEqualTo(200);
    }

    @Test
    @DisplayName("대기열 이외 엔드포인트는 rate limit 적용 안 함")
    void nonQueueEndpoint_notFiltered() throws Exception {
        when(evaluator.isActive()).thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/orders");
        request.addHeader("X-USER-ID", "1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("enabled=false이면 필터 비활성")
    void disabled_allPass() throws Exception {
        RateLimitProperties disabledProps = new RateLimitProperties(
                false, 0.85, 0.60, 10, 1000, 1, 10, 1, 2, 3, 10
        );
        DynamicRateLimitFilter disabledFilter = new DynamicRateLimitFilter(evaluator, disabledProps, queueApp);
        when(evaluator.isActive()).thenReturn(true);

        for (int i = 0; i < 10; i++) {
            MockHttpServletRequest request = createQueueRequest("1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            disabledFilter.doFilter(request, response, new MockFilterChain());
            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Test
    @DisplayName("폴링 429가 abuseResetThreshold 초과 시 순번 리셋 호출")
    void pollingAbuse_resetsPosition() throws Exception {
        when(evaluator.isActive()).thenReturn(false);

        for (int i = 0; i < 8; i++) {
            MockHttpServletRequest request = createPollingRequest("1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
        }

        verify(queueApp).resetPosition(1L);
    }

    @Test
    @DisplayName("폴링 429가 임계값 미만이면 순번 리셋 안 함")
    void pollingUnderThreshold_noReset() throws Exception {
        when(evaluator.isActive()).thenReturn(false);

        for (int i = 0; i < 3; i++) {
            MockHttpServletRequest request = createPollingRequest("1");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
        }

        verify(queueApp, never()).resetPosition(anyLong());
    }

    private MockHttpServletRequest createPollingRequest(String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/queue/position");
        request.addHeader("X-USER-ID", userId);
        return request;
    }

    private MockHttpServletRequest createQueueRequest(String userId) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/queue/enter");
        request.addHeader("X-USER-ID", userId);
        return request;
    }
}

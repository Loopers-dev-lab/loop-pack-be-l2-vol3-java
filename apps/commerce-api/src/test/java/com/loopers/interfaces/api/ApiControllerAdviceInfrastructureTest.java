package com.loopers.interfaces.api;

import com.loopers.infrastructure.metrics.QueueInfrastructureMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link ApiControllerAdvice}의 저장소 예외 핸들러만 {@link MockMvcBuilders#standaloneSetup(Object...)}로 검증한다.
 * 전체 웹 슬라이스({@code @WebMvcTest})는 프로브 컨트롤러 등록 이슈로 사용하지 않는다.
 */
@DisplayName("ApiControllerAdvice: 저장소(Redis/DB) 예외 매핑")
class ApiControllerAdviceInfrastructureTest {

    private final MeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final QueueInfrastructureMetrics metrics = new QueueInfrastructureMetrics(meterRegistry);
    private final ApiControllerAdvice advice = new ApiControllerAdvice(metrics);
    private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new InfrastructureFailureProbeController())
            .setControllerAdvice(advice)
            .build();

    @DisplayName("RedisConnectionFailureException 시 500과 공통 메시지·메트릭을 반환한다.")
    @Test
    void handle_whenRedisConnectionFailure_shouldReturnInternalErrorAndIncrementMetric() throws Exception {
        double before = meterRegistry.counter("loopers.queue.backend.failures", "layer", "api").count();
        assertBackendFailureResponse(
                before,
                mockMvc.perform(get("/__test__/infra/redis-connection-failure"))
        );
    }

    @DisplayName("DataAccessException 시 500과 공통 메시지·메트릭을 반환한다.")
    @Test
    void handle_whenDataAccessFailure_shouldReturnInternalErrorAndIncrementMetric() throws Exception {
        double before = meterRegistry.counter("loopers.queue.backend.failures", "layer", "api").count();
        assertBackendFailureResponse(
                before,
                mockMvc.perform(get("/__test__/infra/data-access-failure"))
        );
    }

    private void assertBackendFailureResponse(double before, ResultActions actions) throws Exception {
        actions.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.meta.result").value("FAIL"))
                .andExpect(jsonPath("$.meta.errorCode").value("Internal Server Error"))
                .andExpect(jsonPath("$.meta.message").value("일시적으로 저장소에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."));

        assertThat(meterRegistry.counter("loopers.queue.backend.failures", "layer", "api").count() - before)
                .isEqualTo(1.0);
    }

    @RestController
    static class InfrastructureFailureProbeController {

        @GetMapping("/__test__/infra/redis-connection-failure")
        void redisConnectionFailure() {
            throw new RedisConnectionFailureException("probe", new RuntimeException("cause"));
        }

        @GetMapping("/__test__/infra/data-access-failure")
        void dataAccessFailure() {
            throw new DataAccessResourceFailureException("probe");
        }
    }
}

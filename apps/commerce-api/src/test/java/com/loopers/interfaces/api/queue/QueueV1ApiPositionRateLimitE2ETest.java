package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.task.scheduling.enabled=false",
        "queue.position.rate-limit.enabled=true",
        "queue.position.rate-limit.max-requests-per-second=2",
        "queue.position.rate-limit.window-seconds=1"
    }
)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class QueueV1ApiPositionRateLimitE2ETest {

    private static final String ENDPOINT_SIGN_UP = "/api/v1/users";
    private static final String ENDPOINT_JOIN_QUEUE = "/api/v1/queue/enter";
    private static final String ENDPOINT_QUEUE_POSITION = "/api/v1/queue/position";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("순번 조회를 초당 상한 초과로 호출하면 429")
    @Test
    void getQueuePosition_exceedsRateLimit_shouldReturn429() {
        UserV1Dto.SignUpRequest signUpRequest = new UserV1Dto.SignUpRequest(
                "rluser",
                "SecurePass1!",
                "rl@example.com",
                "1990-01-15",
                "MALE");
        testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                new HttpEntity<>(signUpRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "rluser");
        testRestTemplate.exchange(
                ENDPOINT_JOIN_QUEUE,
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.JoinQueueResponse>>() {});

        testRestTemplate.exchange(
                ENDPOINT_QUEUE_POSITION,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>>() {});
        testRestTemplate.exchange(
                ENDPOINT_QUEUE_POSITION,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.PositionResponse>>() {});

        ResponseEntity<ApiResponse<Object>> third = testRestTemplate.exchange(
                ENDPOINT_QUEUE_POSITION,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(third.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(third.getHeaders().getFirst("Retry-After")).isEqualTo("1");
        assertThat(third.getBody()).isNotNull();
        assertThat(third.getBody().meta().result()).isEqualTo(ApiResponse.Metadata.Result.FAIL);
        assertThat(third.getBody().meta().errorCode()).isEqualTo("TOO_MANY_REQUESTS");
    }
}

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

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result.SUCCESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.task.scheduling.enabled=false"
)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class QueueV1ApiE2ETest {

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

    @DisplayName("joinQueue_withValidUser_shouldReturnOkWithQueueInfo")
    @Test
    void joinQueue_withValidUser_shouldReturnOkWithQueueInfo() {
        // given
        UserV1Dto.SignUpRequest signUpRequest = new UserV1Dto.SignUpRequest(
            "queueuser1",
            "SecurePass1!",
            "queue1@example.com",
            "1990-01-15",
            "MALE"
        );
        testRestTemplate.exchange(
            ENDPOINT_SIGN_UP,
            HttpMethod.POST,
            new HttpEntity<>(signUpRequest),
            new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {}
        );

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "queueuser1");

        // when
        ResponseEntity<ApiResponse<QueueV1Dto.JoinQueueResponse>> response = testRestTemplate.exchange(
            ENDPOINT_JOIN_QUEUE,
            HttpMethod.POST,
            new HttpEntity<>(headers),
            new ParameterizedTypeReference<>() {}
        );

        // then
        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody()).isNotNull(),
            () -> assertThat(response.getBody().meta().result()).isEqualTo(SUCCESS),
            () -> assertThat(response.getBody().data().position()).isEqualTo(0L),
            () -> assertThat(response.getBody().data().totalWaiting()).isEqualTo(1L)
        );
    }

    @DisplayName("GET /queue/position — 진입 후 순번·폴링 힌트·Retry-After")
    @Test
    void getQueuePosition_afterJoin_shouldReturn200WithHints() {
        UserV1Dto.SignUpRequest signUpRequest = new UserV1Dto.SignUpRequest(
                "qposuser",
                "SecurePass1!",
                "qpos@example.com",
                "1990-01-15",
                "MALE");
        testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                new HttpEntity<>(signUpRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "qposuser");
        testRestTemplate.exchange(
                ENDPOINT_JOIN_QUEUE,
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.JoinQueueResponse>>() {});

        ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> response = testRestTemplate.exchange(
                ENDPOINT_QUEUE_POSITION,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(response.getHeaders().getFirst("Retry-After")).isEqualTo("1"),
                () -> assertThat(response.getBody().meta().result()).isEqualTo(SUCCESS),
                () -> assertThat(response.getBody().data().position()).isEqualTo(0L),
                () -> assertThat(response.getBody().data().suggestedPollIntervalMs()).isEqualTo(1000L)
        );
    }

    @DisplayName("GET /queue/position — 미진입 시 404")
    @Test
    void getQueuePosition_withoutJoin_shouldReturn404() {
        UserV1Dto.SignUpRequest signUpRequest = new UserV1Dto.SignUpRequest(
                "qpos404",
                "SecurePass1!",
                "qpos404@example.com",
                "1990-01-15",
                "MALE");
        testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                new HttpEntity<>(signUpRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "qpos404");

        ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                ENDPOINT_QUEUE_POSITION,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}


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
    private static final String ENDPOINT_QUEUE_STREAM = "/api/v1/queue/position/stream";

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

    @DisplayName("POST /queue/enter — 로그인 헤더 없으면 401")
    @Test
    void joinQueue_withoutLoginHeader_shouldReturn401() {
        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_JOIN_QUEUE,
                HttpMethod.POST,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @DisplayName("GET /queue/position — 로그인 헤더 없으면 401")
    @Test
    void getQueuePosition_withoutLoginHeader_shouldReturn401() {
        ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                ENDPOINT_QUEUE_POSITION,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                new ParameterizedTypeReference<>() {});

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @DisplayName("GET /queue/position/stream — 로그인 헤더 없으면 401")
    @Test
    void streamQueuePosition_withoutLoginHeader_shouldReturn401() {
        ResponseEntity<String> response = testRestTemplate.exchange(
                ENDPOINT_QUEUE_STREAM,
                HttpMethod.GET,
                new HttpEntity<>(new HttpHeaders()),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @DisplayName("GET /queue/position/stream — 미진입 시 not-in-queue 이벤트 후 응답 종료")
    @Test
    void streamQueuePosition_whenNotInQueue_shouldEmitNotInQueueEvent() {
        UserV1Dto.SignUpRequest signUpRequest = new UserV1Dto.SignUpRequest(
                "qsseser",
                "SecurePass1!",
                "qsses@example.com",
                "1990-01-15",
                "MALE");
        testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                new HttpEntity<>(signUpRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "qsseser");

        ResponseEntity<String> response = testRestTemplate.exchange(
                ENDPOINT_QUEUE_STREAM,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isNotNull();
        assertThat(response.getHeaders().getContentType().toString()).contains("text/event-stream");
        assertThat(response.getBody()).contains("not-in-queue");
    }

    /**
     * TC-R6-1: 순번은 로그인 유저 본인 기준만. A가 먼저, B가 나중에 진입하면 각자 자기 순번만 조회한다.
     */
    @DisplayName("GET /queue/position — 두 유저 각각 본인 순번만 조회 (IDOR 회귀)")
    @Test
    void getQueuePosition_whenTwoUsersJoined_eachUserSeesOwnPositionOnly() {
        UserV1Dto.SignUpRequest userA = new UserV1Dto.SignUpRequest(
                "qtwouserA", "SecurePass1!", "qtwoA@example.com", "1990-01-15", "MALE");
        UserV1Dto.SignUpRequest userB = new UserV1Dto.SignUpRequest(
                "qtwouserB", "SecurePass1!", "qtwoB@example.com", "1990-01-15", "MALE");
        testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                new HttpEntity<>(userA),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});
        testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                new HttpEntity<>(userB),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});

        HttpHeaders headersA = new HttpHeaders();
        headersA.set("X-Loopers-LoginId", "qtwouserA");
        HttpHeaders headersB = new HttpHeaders();
        headersB.set("X-Loopers-LoginId", "qtwouserB");

        testRestTemplate.exchange(
                ENDPOINT_JOIN_QUEUE,
                HttpMethod.POST,
                new HttpEntity<>(headersA),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.JoinQueueResponse>>() {});
        testRestTemplate.exchange(
                ENDPOINT_JOIN_QUEUE,
                HttpMethod.POST,
                new HttpEntity<>(headersB),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.JoinQueueResponse>>() {});

        ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> resA = testRestTemplate.exchange(
                ENDPOINT_QUEUE_POSITION,
                HttpMethod.GET,
                new HttpEntity<>(headersA),
                new ParameterizedTypeReference<>() {});
        ResponseEntity<ApiResponse<QueueV1Dto.PositionResponse>> resB = testRestTemplate.exchange(
                ENDPOINT_QUEUE_POSITION,
                HttpMethod.GET,
                new HttpEntity<>(headersB),
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(resA.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(resB.getStatusCode()).isEqualTo(HttpStatus.OK),
                () -> assertThat(resA.getBody().data().position()).isEqualTo(0L),
                () -> assertThat(resB.getBody().data().position()).isEqualTo(1L),
                () -> assertThat(resA.getBody().data().totalWaiting()).isEqualTo(2L),
                () -> assertThat(resB.getBody().data().totalWaiting()).isEqualTo(2L));
    }
}


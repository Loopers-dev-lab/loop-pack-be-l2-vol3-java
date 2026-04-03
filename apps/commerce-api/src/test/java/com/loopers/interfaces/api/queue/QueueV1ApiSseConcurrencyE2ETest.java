package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result.FAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

/**
 * T1: {@code queue.position.sse-stream.max-concurrent-connections}로 SSE 동시 연결 상한을 검증한다.
 * 대기열에 머문 유저의 스트림이 permit을 점유하므로, 세 번째 연결은 429가 된다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.task.scheduling.enabled=false",
                "queue.position.sse-stream.max-concurrent-connections=2"
        }
)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class QueueV1ApiSseConcurrencyE2ETest {

    private static final String ENDPOINT_SIGN_UP = "/api/v1/users";
    private static final String ENDPOINT_JOIN_QUEUE = "/api/v1/queue/enter";
    private static final String STREAM_PATH = "/api/v1/queue/position/stream";

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private HttpClient httpClient;

    @BeforeEach
    void initClient() {
        httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("SSE 동시 연결 2개 점유 시 세 번째 연결은 429 + TOO_MANY_REQUESTS")
    @Test
    void streamQueuePosition_whenTwoStreamsHeld_thirdReturns429() throws Exception {
        signUpAndJoin("ssecc01");
        signUpAndJoin("ssecc02");
        signUpAndJoin("ssecc03");

        URI streamUri = URI.create("http://localhost:" + port + STREAM_PATH);

        CompletableFuture<HttpResponse<InputStream>> f1 = openHoldStream(streamUri, "ssecc01");
        CompletableFuture<HttpResponse<InputStream>> f2 = openHoldStream(streamUri, "ssecc02");

        HttpResponse<InputStream> h1 = f1.get(30, TimeUnit.SECONDS);
        HttpResponse<InputStream> h2 = f2.get(30, TimeUnit.SECONDS);
        assertThat(h1.statusCode()).isEqualTo(200);
        assertThat(h2.statusCode()).isEqualTo(200);

        HttpHeaders headers3 = new HttpHeaders();
        headers3.set("X-Loopers-LoginId", "ssecc03");
        // stream 엔드포인트는 text/event-stream만 produces 하므로, 기본 Accept(application/json)만 보내면 매핑이 안 된다.
        // 한도 초과 시에는 ApiControllerAdvice가 JSON을 내려주므로 */* 로 협상한다.
        headers3.set(HttpHeaders.ACCEPT, MediaType.ALL_VALUE);
        ResponseEntity<ApiResponse<Object>> third = testRestTemplate.exchange(
                STREAM_PATH,
                HttpMethod.GET,
                new HttpEntity<>(headers3),
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(third.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS),
                () -> assertThat(third.getHeaders().getFirst("Retry-After")).isEqualTo("1"),
                () -> assertThat(third.getBody()).isNotNull(),
                () -> assertThat(third.getBody().meta().result()).isEqualTo(FAIL),
                () -> assertThat(third.getBody().meta().errorCode()).isEqualTo("TOO_MANY_REQUESTS"));

        try {
            h1.body().close();
        } finally {
            h2.body().close();
        }
    }

    private void signUpAndJoin(String loginId) {
        UserV1Dto.SignUpRequest signUpRequest = new UserV1Dto.SignUpRequest(
                loginId, "SecurePass1!", loginId + "@example.com", "1990-01-15", "MALE");
        testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                new HttpEntity<>(signUpRequest),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        testRestTemplate.exchange(
                ENDPOINT_JOIN_QUEUE,
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<ApiResponse<QueueV1Dto.JoinQueueResponse>>() {});
    }

    private CompletableFuture<HttpResponse<InputStream>> openHoldStream(URI streamUri, String loginId) {
        HttpRequest req =
                HttpRequest.newBuilder(streamUri).header("X-Loopers-LoginId", loginId).GET().build();
        return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofInputStream());
    }
}

package com.loopers.interfaces.api;

import com.loopers.application.queue.QueueService;
import com.loopers.application.queue.QueueStatus;
import com.loopers.domain.user.UserModel;
import com.loopers.infrastructure.user.UserJpaRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RedisTestContainersConfig.class)
class QueueV1ApiE2ETest {

    private static final String ENDPOINT_QUEUE_ENTER = "/api/v1/queue/enter";
    private static final String ENDPOINT_QUEUE_POSITION = "/api/v1/queue/position";
    private static final String ENDPOINT_QUEUE_COUNT = "/api/v1/queue/count";
    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @Autowired
    private QueueService queueService;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
    }

    @DisplayName("POST /api/v1/queue/enter - 중복 진입 없이 대기열 순번을 반환한다.")
    @Test
    void entersQueueWithoutDuplicate() {
        createUser("testuser", "Test1234!");

        ParameterizedTypeReference<ApiResponse<QueuePositionResponse>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<QueuePositionResponse>> first = testRestTemplate.exchange(
            ENDPOINT_QUEUE_ENTER,
            HttpMethod.POST,
            new HttpEntity<>(authHeaders("testuser", "Test1234!")),
            responseType
        );
        ResponseEntity<ApiResponse<QueuePositionResponse>> second = testRestTemplate.exchange(
            ENDPOINT_QUEUE_ENTER,
            HttpMethod.POST,
            new HttpEntity<>(authHeaders("testuser", "Test1234!")),
            responseType
        );

        assertAll(
            () -> assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(first.getBody().data().status()).isEqualTo(QueueStatus.WAITING),
            () -> assertThat(first.getBody().data().position()).isEqualTo(1L),
            () -> assertThat(second.getBody().data().position()).isEqualTo(1L),
            () -> assertThat(second.getBody().data().totalWaiting()).isEqualTo(1L)
        );
    }

    @DisplayName("GET /api/v1/queue/position - 토큰 발급 후 상태와 토큰을 반환한다.")
    @Test
    void returnsIssuedToken_whenUserIsAdmitted() {
        createUser("testuser", "Test1234!");
        queueService.enter("testuser", "Test1234!");
        queueService.admitNextBatch(1);

        ParameterizedTypeReference<ApiResponse<QueuePositionResponse>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<QueuePositionResponse>> response = testRestTemplate.exchange(
            ENDPOINT_QUEUE_POSITION,
            HttpMethod.GET,
            new HttpEntity<>(authHeaders("testuser", "Test1234!")),
            responseType
        );

        assertAll(
            () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
            () -> assertThat(response.getBody().data().status()).isEqualTo(QueueStatus.ENTERED),
            () -> assertThat(response.getBody().data().token()).isNotBlank()
        );
    }

    @DisplayName("GET /api/v1/queue/count - 전체 대기 인원을 반환한다.")
    @Test
    void returnsTotalWaitingCount() {
        createUser("user1", "Test1234!");
        createUser("user2", "Test1234!");
        queueService.enter("user1", "Test1234!");
        queueService.enter("user2", "Test1234!");

        ParameterizedTypeReference<ApiResponse<QueueCountResponse>> responseType = new ParameterizedTypeReference<>() {};
        ResponseEntity<ApiResponse<QueueCountResponse>> response = testRestTemplate.exchange(
            ENDPOINT_QUEUE_COUNT,
            HttpMethod.GET,
            null,
            responseType
        );

        assertThat(response.getBody().data().totalWaiting()).isEqualTo(2L);
    }

    private UserModel createUser(String loginId, String rawPassword) {
        String encodedPassword = passwordEncoder.encode(rawPassword);
        return userJpaRepository.save(
            UserModel.createWithEncodedPassword(loginId, encodedPassword, "사용자", LocalDate.of(1990, 1, 1), loginId + "@example.com")
        );
    }

    private HttpHeaders authHeaders(String loginId, String password) {
        HttpHeaders headers = new HttpHeaders();
        headers.set(HEADER_LOGIN_ID, loginId);
        headers.set(HEADER_LOGIN_PW, password);
        return headers;
    }

    record QueuePositionResponse(
        Long userId,
        QueueStatus status,
        Long position,
        Long totalWaiting,
        Long estimatedWaitSeconds,
        String token,
        Integer recommendedPollingSeconds
    ) {}

    record QueueCountResponse(Long totalWaiting) {}
}

package com.loopers.interfaces.api.queue;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.user.UserV1Dto;
import com.loopers.testcontainers.MySqlTestContainersConfig;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.support.error.ErrorType;
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

import static com.loopers.interfaces.api.ApiResponse.Metadata.Result.FAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.task.scheduling.enabled=false",
        "queue.join.max-waiting=2",
        "queue.fallback.enabled=false"
    }
)
@Import({MySqlTestContainersConfig.class, RedisTestContainersConfig.class})
class QueueV1ApiJoinCapacityE2ETest {

    private static final String ENDPOINT_SIGN_UP = "/api/v1/users";
    private static final String ENDPOINT_JOIN_QUEUE = "/api/v1/queue/enter";

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

    @DisplayName("정원 초과 시 POST /queue/enter 는 409 CONFLICT")
    @Test
    void joinQueue_whenCapacityFull_shouldReturn409() {
        signUp("capuser1", "cap1@example.com");
        signUp("capuser2", "cap2@example.com");
        signUp("capuser3", "cap3@example.com");

        assertThat(join("capuser1").getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(join("capuser2").getStatusCode()).isEqualTo(HttpStatus.OK);

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", "capuser3");
        ResponseEntity<ApiResponse<Void>> third = testRestTemplate.exchange(
                ENDPOINT_JOIN_QUEUE,
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});

        assertAll(
                () -> assertThat(third.getStatusCode()).isEqualTo(HttpStatus.CONFLICT),
                () -> assertThat(third.getBody()).isNotNull(),
                () -> assertThat(third.getBody().meta().result()).isEqualTo(FAIL),
                () -> assertThat(third.getBody().meta().errorCode()).isEqualTo(ErrorType.CONFLICT.getCode())
        );
    }

    private void signUp(String loginId, String email) {
        UserV1Dto.SignUpRequest req = new UserV1Dto.SignUpRequest(
                loginId,
                "SecurePass1!",
                email,
                "1990-01-15",
                "MALE");
        testRestTemplate.exchange(
                ENDPOINT_SIGN_UP,
                HttpMethod.POST,
                new HttpEntity<>(req),
                new ParameterizedTypeReference<ApiResponse<UserV1Dto.SignUpResponse>>() {});
    }

    private ResponseEntity<ApiResponse<QueueV1Dto.JoinQueueResponse>> join(String loginId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-LoginId", loginId);
        return testRestTemplate.exchange(
                ENDPOINT_JOIN_QUEUE,
                HttpMethod.POST,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
    }
}

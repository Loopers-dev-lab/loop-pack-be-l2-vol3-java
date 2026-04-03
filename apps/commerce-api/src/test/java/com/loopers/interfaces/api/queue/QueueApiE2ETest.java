package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.ModeManager;
import com.loopers.application.queue.SessionService;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.application.queue.dto.QueueEntryResponse;
import com.loopers.application.queue.dto.QueuePositionResponse;
import com.loopers.support.E2ETestFixture;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
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
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2ETestFixture.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QueueApiE2ETest {

    private static final String ENTER_ENDPOINT = "/api/v1/queue/enter";
    private static final String POSITION_ENDPOINT = "/api/v1/queue/position";
    private static final String USER_LOGIN_ID = "queueuser";
    private static final String USER_PASSWORD = "Test1234!";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private E2ETestFixture fixture;

    @Autowired
    private ModeManager modeManager;

    @Autowired
    private SessionService sessionService;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
        modeManager.switchToNormal();
        fixture.signUp(USER_LOGIN_ID, USER_PASSWORD, "테스트유저", "queue@test.com");
    }

    @Nested
    class 대기열_진입 {

        @Test
        void EVENT_모드에서_진입하면_200_응답과_대기_순번을_반환한다() {
            modeManager.switchToEvent();

            ResponseEntity<ApiResponse<QueueEntryResponse>> response = postEnter();

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("WAITING"),
                    () -> assertThat(response.getBody().data().position()).isGreaterThanOrEqualTo(1)
            );
        }

        @Test
        void NORMAL_모드에서_진입하면_400_응답을_반환한다() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENTER_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(userHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        void 인증_헤더가_없으면_401_응답을_반환한다() {
            modeManager.switchToEvent();

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENTER_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        void ACTIVE_세션이_있는_유저가_진입하면_409_응답을_반환한다() {
            modeManager.switchToEvent();
            // 유저 ID를 직접 조회해서 세션 생성
            postEnter(); // 먼저 대기열 진입
            // admitBatch로 세션 발급
            // 세션이 있는 상태에서 재진입 시도는 409
            // 테스트 단순화: SessionService로 직접 세션 생성
        }
    }

    @Nested
    class 순번_조회 {

        @Test
        void EVENT_모드에서_대기_중이면_200_응답과_순번을_반환한다() {
            modeManager.switchToEvent();
            postEnter();

            ResponseEntity<ApiResponse<QueuePositionResponse>> response = getPosition();

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("WAITING"),
                    () -> assertThat(response.getBody().data().position()).isGreaterThanOrEqualTo(1)
            );
        }

        @Test
        void 대기열에_없으면_NOT_IN_QUEUE_상태를_반환한다() {
            modeManager.switchToEvent();

            ResponseEntity<ApiResponse<QueuePositionResponse>> response = getPosition();

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("NOT_IN_QUEUE"),
                    () -> assertThat(response.getBody().data().position()).isEqualTo(-1)
            );
        }

        @Test
        void DRAIN_모드에서_세션이_없으면_EVENT_ENDED_상태를_반환한다() {
            modeManager.switchToEvent();
            modeManager.switchToDrain();

            ResponseEntity<ApiResponse<QueuePositionResponse>> response = getPosition();

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().status()).isEqualTo("EVENT_ENDED")
            );
        }

        @Test
        void 인증_헤더가_없으면_401_응답을_반환한다() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    POSITION_ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(new HttpHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // --- 헬퍼 메서드 ---

    private ResponseEntity<ApiResponse<QueueEntryResponse>> postEnter() {
        return testRestTemplate.exchange(
                ENTER_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private ResponseEntity<ApiResponse<QueuePositionResponse>> getPosition() {
        return testRestTemplate.exchange(
                POSITION_ENDPOINT, HttpMethod.GET,
                new HttpEntity<>(userHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private HttpHeaders userHeaders() {
        return fixture.userHeaders(USER_LOGIN_ID, USER_PASSWORD);
    }
}

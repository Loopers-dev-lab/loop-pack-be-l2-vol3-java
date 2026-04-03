package com.loopers.interfaces.api.queue;

import com.loopers.application.queue.ModeManager;
import com.loopers.interfaces.api.ApiResponse;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2ETestFixture.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class QueueAdminApiE2ETest {

    private static final String MODE_ENDPOINT = "/api-admin/v1/queue/mode";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private E2ETestFixture fixture;

    @Autowired
    private ModeManager modeManager;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
        redisCleanUp.truncateAll();
        modeManager.switchToNormal();
    }

    @Nested
    class 모드_전환 {

        @Test
        void EVENT_모드로_전환하면_200_응답을_반환한다() {
            ResponseEntity<ApiResponse<Void>> response = changeMode("EVENT");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(modeManager.isEvent()).isTrue()
            );
        }

        @Test
        void DRAIN_모드로_전환하면_200_응답을_반환한다() {
            changeMode("EVENT");

            ResponseEntity<ApiResponse<Void>> response = changeMode("DRAIN");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(modeManager.isDrain()).isTrue()
            );
        }

        @Test
        void NORMAL_모드로_전환하면_200_응답을_반환한다() {
            changeMode("EVENT");

            ResponseEntity<ApiResponse<Void>> response = changeMode("NORMAL");

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(modeManager.isEvent()).isFalse(),
                    () -> assertThat(modeManager.isDrain()).isFalse()
            );
        }

        @Test
        void 관리자_인증_헤더가_없으면_401_응답을_반환한다() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    MODE_ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(Map.of("mode", "EVENT"), jsonHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // --- 헬퍼 메서드 ---

    private ResponseEntity<ApiResponse<Void>> changeMode(String mode) {
        HttpHeaders headers = fixture.adminHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return testRestTemplate.exchange(
                MODE_ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(Map.of("mode", mode), headers),
                new ParameterizedTypeReference<>() {}
        );
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }
}

package com.loopers.interfaces.api.ranking;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.E2ETestFixture;
import com.loopers.utils.DatabaseCleanUp;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(E2ETestFixture.class)
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class WeightConfigAdminApiE2ETest {

    private static final String ENDPOINT = "/api-admin/v1/ranking/weights";

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private E2ETestFixture fixture;

    @BeforeEach
    void setUp() {
        databaseCleanUp.truncateAllTables();
    }

    @Nested
    class 그룹_생성 {

        @Test
        void 유효한_정보로_생성하면_200_응답한다() {
            ResponseEntity<ApiResponse<WeightConfigV1Dto.Response>> response = createConfig(
                    "experiment", 0.5, 0.3, 0.2, 50);

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().groupName()).isEqualTo("experiment"),
                    () -> assertThat(response.getBody().data().wView()).isEqualTo(0.5),
                    () -> assertThat(response.getBody().data().active()).isTrue()
            );
        }

        @Test
        void 이미_존재하는_그룹명으로_생성하면_409_응답한다() {
            createConfig("experiment", 0.5, 0.3, 0.2, 50);

            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.POST,
                    new HttpEntity<>(Map.of(
                            "groupName", "experiment",
                            "wView", 0.1, "wLike", 0.2, "wOrder", 0.7, "trafficPct", 30
                    ), adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        }
    }

    @Nested
    class 그룹_수정 {

        @Test
        void 존재하는_그룹의_가중치를_수정하면_200_응답한다() {
            createConfig("experiment", 0.5, 0.3, 0.2, 50);

            ResponseEntity<ApiResponse<WeightConfigV1Dto.Response>> response = testRestTemplate.exchange(
                    ENDPOINT + "/experiment", HttpMethod.PUT,
                    new HttpEntity<>(Map.of(
                            "wView", 0.1, "wLike", 0.5, "wOrder", 0.4, "trafficPct", 30
                    ), adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data().wLike()).isEqualTo(0.5),
                    () -> assertThat(response.getBody().data().trafficPct()).isEqualTo(30)
            );
        }

        @Test
        void 존재하지_않는_그룹을_수정하면_404_응답한다() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/nonexistent", HttpMethod.PUT,
                    new HttpEntity<>(Map.of(
                            "wView", 0.1, "wLike", 0.2, "wOrder", 0.7, "trafficPct", 100
                    ), adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    @Nested
    class 그룹_비활성화 {

        @Test
        void 실험_그룹을_비활성화하면_200_응답한다() {
            createConfig("experiment", 0.5, 0.3, 0.2, 50);

            ResponseEntity<ApiResponse<Void>> response = testRestTemplate.exchange(
                    ENDPOINT + "/experiment", HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        void control_그룹을_비활성화하면_400_응답한다() {
            ResponseEntity<ApiResponse<Object>> response = testRestTemplate.exchange(
                    ENDPOINT + "/control", HttpMethod.DELETE,
                    new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    @Nested
    class 활성_그룹_조회 {

        @Test
        void 활성_그룹_목록을_200_응답한다() {
            createConfig("control", 0.1, 0.2, 0.7, 50);
            createConfig("experiment", 0.5, 0.3, 0.2, 50);

            ResponseEntity<ApiResponse<List<WeightConfigV1Dto.Response>>> response = testRestTemplate.exchange(
                    ENDPOINT, HttpMethod.GET,
                    new HttpEntity<>(adminHeaders()),
                    new ParameterizedTypeReference<>() {}
            );

            assertAll(
                    () -> assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK),
                    () -> assertThat(response.getBody().data()).hasSize(2)
            );
        }
    }

    // --- 헬퍼 메서드 ---

    private ResponseEntity<ApiResponse<WeightConfigV1Dto.Response>> createConfig(
            String groupName, double wView, double wLike, double wOrder, int trafficPct) {
        return testRestTemplate.exchange(
                ENDPOINT, HttpMethod.POST,
                new HttpEntity<>(Map.of(
                        "groupName", groupName,
                        "wView", wView, "wLike", wLike, "wOrder", wOrder,
                        "trafficPct", trafficPct
                ), adminHeaders()),
                new ParameterizedTypeReference<>() {}
        );
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Loopers-Ldap", "admin-ldap");
        return headers;
    }
}

package com.loopers.interfaces.api;

import com.loopers.interfaces.api.coderabbit.CodeRabbitV1Dto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CodeRabbitV1ApiE2ETest {

    private static final String ENDPOINT_PING = "/api/v1/code-rabbit/ping";
    private static final String ENDPOINT_HELLO = "/api/v1/code-rabbit/hello";
    private static final String ENDPOINT_STATUS = "/api/v1/code-rabbit/status";

    private final TestRestTemplate testRestTemplate;

    @Autowired
    public CodeRabbitV1ApiE2ETest(TestRestTemplate testRestTemplate) {
        this.testRestTemplate = testRestTemplate;
    }

    @DisplayName("GET /api/v1/code-rabbit/ping")
    @Nested
    class Ping {
        @DisplayName("정상 호출 시 pong 메시지와 timestamp를 반환한다.")
        @Test
        void returnsPongMessageAndTimestamp_whenCalled() {
            // act
            ParameterizedTypeReference<ApiResponse<CodeRabbitV1Dto.PingResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CodeRabbitV1Dto.PingResponse>> response =
                testRestTemplate.exchange(ENDPOINT_PING, HttpMethod.GET, new HttpEntity<>(null), responseType);

            // assert
            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().message()).isEqualTo("pong from code rabbit test api"),
                () -> assertThat(response.getBody().data().timestamp()).isPositive()
            );
        }
    }

    @DisplayName("GET /api/v1/code-rabbit/hello")
    @Nested
    class Hello {
        @DisplayName("정상 호출 시 greeting과 target을 반환한다.")
        @Test
        void returnsGreetingAndTarget_whenCalled() {
            ParameterizedTypeReference<ApiResponse<CodeRabbitV1Dto.HelloResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CodeRabbitV1Dto.HelloResponse>> response =
                testRestTemplate.exchange(ENDPOINT_HELLO, HttpMethod.GET, new HttpEntity<>(null), responseType);

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().greeting()).isEqualTo("Hello"),
                () -> assertThat(response.getBody().data().target()).isEqualTo("CodeRabbit")
            );
        }
    }

    @DisplayName("GET /api/v1/code-rabbit/status")
    @Nested
    class Status {
        @DisplayName("정상 호출 시 status와 version을 반환한다.")
        @Test
        void returnsStatusAndVersion_whenCalled() {
            ParameterizedTypeReference<ApiResponse<CodeRabbitV1Dto.StatusResponse>> responseType = new ParameterizedTypeReference<>() {};
            ResponseEntity<ApiResponse<CodeRabbitV1Dto.StatusResponse>> response =
                testRestTemplate.exchange(ENDPOINT_STATUS, HttpMethod.GET, new HttpEntity<>(null), responseType);

            assertAll(
                () -> assertTrue(response.getStatusCode().is2xxSuccessful()),
                () -> assertThat(response.getBody()).isNotNull(),
                () -> assertThat(response.getBody().data().status()).isEqualTo("UP"),
                () -> assertThat(response.getBody().data().version()).isEqualTo("1.0.0")
            );
        }
    }
}

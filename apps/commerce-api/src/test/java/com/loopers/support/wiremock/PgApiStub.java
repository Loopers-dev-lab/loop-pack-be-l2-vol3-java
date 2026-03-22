package com.loopers.support.wiremock;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;

/**
 * PG 결제 API의 WireMock 스텁과 요청 검증을 도메인 용어로 제공한다.
 *
 * <p>WireMock DSL({@code stubFor}, {@code post}, {@code aResponse} 등)을 내부에 캡슐화하여,
 * 테스트 코드에서 WireMock을 몰라도 PG API의 동작을 설정하고 검증할 수 있다.
 *
 * <p>사용 예시:
 * <pre>{@code
 * PgApiStub pgStub = new PgApiStub(wireMock);
 *
 * // PG가 정상 응답하도록 설정
 * pgStub.willRespondSuccess("txn-001");
 *
 * // PG가 400 에러를 반환하도록 설정
 * pgStub.willRespondError(400, "INVALID_CARD", "유효하지 않은 카드");
 *
 * // PG에 요청이 전달되었는지 검증
 * pgStub.verifyPaymentRequested("1");
 * }</pre>
 */
public class PgApiStub {

    private static final String PAYMENT_URL = "/api/v1/payments";

    private final WireMockExtension wireMock;

    public PgApiStub(WireMockExtension wireMock) {
        this.wireMock = wireMock;
    }

    /**
     * 모든 스텁과 요청 기록을 초기화한다.
     *
     * <p>각 테스트의 {@code @BeforeEach}에서 호출하여 테스트 간 격리를 보장한다.
     */
    public void resetAll() {
        wireMock.resetAll();
    }

    /**
     * PG 결제 요청에 대해 성공 응답(200)을 반환하도록 스텁을 설정한다.
     *
     * <p>응답 데이터의 status는 {@code PENDING}, reason은 {@code null}로 설정된다.
     *
     * @param transactionKey 응답에 포함될 PG 트랜잭션 키
     */
    public void willRespondSuccess(String transactionKey) {
        String body = """
                {"meta":{"result":"SUCCESS","errorCode":null,"message":null},\
                "data":{"transactionKey":"%s","status":"PENDING","reason":null}}"""
                .formatted(transactionKey);
        stubPayment(200, body);
    }

    /**
     * PG 결제 요청에 대해 에러 응답을 반환하도록 스텁을 설정한다.
     *
     * @param status    HTTP 상태 코드 (예: 400, 500)
     * @param errorCode PG 에러 코드 (예: {@code "INVALID_CARD"}, {@code "INTERNAL_ERROR"})
     * @param message   에러 메시지 (예: {@code "유효하지 않은 카드"})
     */
    public void willRespondError(int status, String errorCode, String message) {
        String body = """
                {"meta":{"result":"FAIL","errorCode":"%s","message":"%s"},"data":null}"""
                .formatted(errorCode, message);
        stubPayment(status, body);
    }

    /**
     * PG 결제 API에 요청이 전달되었는지 검증하고, {@code X-USER-ID} 헤더 값을 확인한다.
     *
     * @param expectedUserId 기대하는 {@code X-USER-ID} 헤더 값
     */
    public void verifyPaymentRequested(String expectedUserId) {
        wireMock.verify(postRequestedFor(urlEqualTo(PAYMENT_URL))
                .withHeader("X-USER-ID", equalTo(expectedUserId)));
    }

    private void stubPayment(int status, String body) {
        wireMock.stubFor(post(urlEqualTo(PAYMENT_URL))
                .willReturn(WireMock.aResponse()
                        .withStatus(status)
                        .withHeader("Content-Type", "application/json")
                        .withBody(body)));
    }
}

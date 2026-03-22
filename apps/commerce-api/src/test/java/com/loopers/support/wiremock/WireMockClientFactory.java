package com.loopers.support.wiremock;

import java.time.Duration;

import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * WireMock과 호환되는 {@link HttpServiceProxyFactory} 기반 HTTP 인터페이스 프록시를 생성하는 팩토리.
 *
 * <p>Spring Boot 3.4.x + Java 21 환경에서 {@code ClientHttpRequestFactoryBuilder.detect()}는
 * {@code JdkClientHttpRequestFactory}를 반환하는데, 이 팩토리가 {@code HttpServiceProxyFactory} 프록시를 통해
 * WireMock에 요청하면 EOF 에러가 발생한다. 이 팩토리는 {@link SimpleClientHttpRequestFactory}(HTTP/1.1)를
 * 사용하여 이 문제를 우회한다.
 *
 * <p>사용 예시:
 * <pre>{@code
 * @TestConfiguration
 * static class WireMockConfig {
 *     @Bean
 *     @Primary
 *     SomeHttpInterface wireMockClient(
 *             @Value("${some.base-url}") String baseUrl,
 *             @Value("${some.connect-timeout}") int connectTimeout,
 *             @Value("${some.read-timeout}") int readTimeout
 *     ) {
 *         return WireMockClientFactory.create(SomeHttpInterface.class, baseUrl, connectTimeout, readTimeout);
 *     }
 * }
 * }</pre>
 */
public class WireMockClientFactory {

    private WireMockClientFactory() {
    }

    /**
     * 지정된 HttpInterface 타입의 WireMock 호환 프록시를 생성한다.
     *
     * @param clientType     생성할 HTTP 인터페이스 클래스
     * @param baseUrl        WireMock 서버 URL ({@code @DynamicPropertySource}로 주입)
     * @param connectTimeout 연결 타임아웃 (ms)
     * @param readTimeout    읽기 타임아웃 (ms)
     * @param <T>            HTTP 인터페이스 타입
     * @return WireMock과 통신하는 HTTP 인터페이스 프록시
     */
    public static <T> T create(Class<T> clientType, String baseUrl, int connectTimeout, int readTimeout) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeout));
        factory.setReadTimeout(Duration.ofMillis(readTimeout));

        RestClient restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(clientType);
    }
}

package com.loopers.config;

import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class PgClientConfig {

    /**
     * PG 전용 RestTemplate (커넥션 풀 + 타임아웃 설정).
     *
     * 타임아웃:
     *   - Connection Timeout (1초): TCP handshake 대기. PG 서버 다운 시 빠른 실패
     *   - Read Timeout (2초): 응답 대기. PG 지연(100~500ms) p99 + 여유분
     *   - Connection Request Timeout (1초): 풀에서 커넥션 얻는 대기. 풀 포화 시 빠른 실패
     *
     * 커넥션 풀:
     *   - maxConnTotal (20): 전체 최대 연결 수. PG 서버 수용 한계 고려
     *   - maxConnPerRoute (20): 단일 호스트(PG) 최대 연결. 단일 PG이므로 total과 동일
     */
    @Bean
    public RestTemplate pgRestTemplate(
        @Value("${pg.connect-timeout}") int connectTimeout,
        @Value("${pg.read-timeout}") int readTimeout,
        @Value("${pg.pool.max-connections:20}") int maxConnections,
        @Value("${pg.pool.connection-request-timeout:1000}") int connectionRequestTimeout
    ) {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(maxConnections);
        connectionManager.setDefaultMaxPerRoute(maxConnections);

        RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(connectTimeout))
            .setConnectionRequestTimeout(Timeout.ofMilliseconds(connectionRequestTimeout))
            .setResponseTimeout(Timeout.ofMilliseconds(readTimeout))
            .build();

        CloseableHttpClient httpClient = HttpClients.custom()
            .setConnectionManager(connectionManager)
            .setDefaultRequestConfig(requestConfig)
            .build();

        return new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
    }
}

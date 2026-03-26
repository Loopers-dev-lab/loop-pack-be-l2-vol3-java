package com.loopers.infrastructure.pg.toss;

import feign.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * Toss Sandbox Feign Client 타임아웃 설정.
 *
 * <p>connectTimeout 500ms: TCP 연결 수립 제한.</p>
 * <p>readTimeout 2,000ms: Toss 동기 결제 응답 대기 (Simulator보다 넉넉히).</p>
 *
 * @see <a href="05-payment-resilience.md §5.2">Timeout 값 결정 근거</a>
 */
public class TossSandboxPgConfig {

    @Bean
    public Request.Options tossRequestOptions(
        @Value("${pg.toss.connect-timeout:500}") int connectTimeout,
        @Value("${pg.toss.read-timeout:2000}") int readTimeout
    ) {
        return new Request.Options(connectTimeout, TimeUnit.MILLISECONDS,
                                   readTimeout, TimeUnit.MILLISECONDS, true);
    }
}

package com.loopers.infrastructure.pg.simulator;

import feign.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import java.util.concurrent.TimeUnit;

/**
 * PG Simulator Feign Client 타임아웃 설정.
 *
 * <p>connectTimeout 500ms: TCP 연결 수립 제한. PG가 살아있으면 수십ms 내 완료.</p>
 * <p>readTimeout 1,000ms: PG 응답 대기 제한. 정상 응답 100~500ms의 2배 여유.</p>
 *
 * @see <a href="05-payment-resilience.md §5.2">Timeout 값 결정 근거</a>
 */
public class SimulatorFeignConfig {

    @Bean
    public Request.Options simulatorRequestOptions(
        @Value("${pg.simulator.connect-timeout:500}") int connectTimeout,
        @Value("${pg.simulator.read-timeout:1000}") int readTimeout
    ) {
        return new Request.Options(connectTimeout, TimeUnit.MILLISECONDS,
                                   readTimeout, TimeUnit.MILLISECONDS, true);
    }
}

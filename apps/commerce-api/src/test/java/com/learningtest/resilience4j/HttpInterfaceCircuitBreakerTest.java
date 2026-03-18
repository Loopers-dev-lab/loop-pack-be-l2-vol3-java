package com.learningtest.resilience4j;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import com.loopers.CommerceApiApplication;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;

@SpringBootTest(classes = CommerceApiApplication.class)
@Import(HttpInterfaceCircuitBreakerTest.Config.class)
class HttpInterfaceCircuitBreakerTest {

    private static final int CALL_COUNT = 5;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @Autowired
    private DirectCbHttpInterface directCbInterface;

    @Autowired
    private WrapperClient wrapperClient;

    @BeforeEach
    void setUp() {
        circuitBreakerRegistry.circuitBreaker("test-direct-on-interface").reset();
        circuitBreakerRegistry.circuitBreaker("test-on-wrapper").reset();
    }

    @HttpExchange("/test")
    interface DirectCbHttpInterface {
        @CircuitBreaker(name = "test-direct-on-interface")
        @GetExchange("/ping")
        String ping();
    }

    @HttpExchange("/test")
    interface PlainHttpInterface {
        @GetExchange("/ping")
        String ping();
    }

    static class WrapperClient {
        private final PlainHttpInterface delegate;

        WrapperClient(PlainHttpInterface delegate) {
            this.delegate = delegate;
        }

        @CircuitBreaker(name = "test-on-wrapper")
        public String ping() {
            return delegate.ping();
        }
    }

    @TestConfiguration
    static class Config {

        private RestClient unreachableRestClient() {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(500);

            return RestClient.builder()
                    .baseUrl("http://localhost:1")
                    .requestFactory(requestFactory)
                    .build();
        }

        @Bean
        DirectCbHttpInterface directCbHttpInterface() {
            return HttpServiceProxyFactory
                    .builderFor(RestClientAdapter.create(unreachableRestClient()))
                    .build()
                    .createClient(DirectCbHttpInterface.class);
        }

        @Bean
        PlainHttpInterface plainHttpInterface() {
            return HttpServiceProxyFactory
                    .builderFor(RestClientAdapter.create(unreachableRestClient()))
                    .build()
                    .createClient(PlainHttpInterface.class);
        }

        @Bean
        WrapperClient wrapperClient(PlainHttpInterface plainHttpInterface) {
            return new WrapperClient(plainHttpInterface);
        }
    }

    @DisplayName("HttpInterface에 직접 @CircuitBreaker를 선언하면,")
    @Nested
    class DirectOnHttpInterface {

        @DisplayName("@Bean으로 등록된 HttpInterface 프록시에도 Spring AOP가 적용되어 서킷브레이커가 호출을 기록한다.")
        @Test
        void circuitBreakerRecordsCalls() {
            // act
            for (int i = 0; i < CALL_COUNT; i++) {
                try {
                    directCbInterface.ping();
                } catch (ResourceAccessException ignored) {
                }
            }

            // assert
            long bufferedCalls = circuitBreakerRegistry
                    .circuitBreaker("test-direct-on-interface")
                    .getMetrics()
                    .getNumberOfBufferedCalls();

            assertThat(bufferedCalls)
                    .as("@Bean으로 등록된 HttpInterface 프록시에 선언된 @CircuitBreaker는 Spring AOP가 적용되어 호출이 기록됨")
                    .isEqualTo(CALL_COUNT);
        }
    }

    @DisplayName("래퍼 클래스에 @CircuitBreaker를 선언하면,")
    @Nested
    class OnWrapperClass {

        @DisplayName("Spring AOP가 적용되어 서킷브레이커가 호출을 정상 기록한다.")
        @Test
        void circuitBreakerRecordsCalls() {
            // act
            for (int i = 0; i < CALL_COUNT; i++) {
                try {
                    wrapperClient.ping();
                } catch (ResourceAccessException ignored) {
                }
            }

            // assert
            long bufferedCalls = circuitBreakerRegistry
                    .circuitBreaker("test-on-wrapper")
                    .getMetrics()
                    .getNumberOfBufferedCalls();

            assertThat(bufferedCalls)
                    .as("래퍼 클래스에 선언된 @CircuitBreaker는 Spring AOP가 적용되어 호출이 기록됨")
                    .isEqualTo(CALL_COUNT);
        }
    }
}

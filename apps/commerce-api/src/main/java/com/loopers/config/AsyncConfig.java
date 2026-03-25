package com.loopers.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 비동기 이벤트 처리 설정
 *
 * @Async + @TransactionalEventListener 조합을 위해 필요.
 * 이벤트 리스너에서 @Async를 사용하면 별도 스레드에서 실행되어
 * 메인 트랜잭션과 분리된다.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}

package com.loopers.batch.config;

import org.springframework.context.annotation.Configuration;

/**
 * Spring Batch 설정 클래스.
 *
 * Spring Boot 3.x + Spring Batch 5.x 기준:
 * - Spring Boot의 자동 구성을 활용하여 Batch 인프라 빈 자동 구성
 * - @Primary DataSource와 기본 TransactionManager 사용
 * - 메타 테이블(BATCH_JOB_INSTANCE, BATCH_JOB_EXECUTION 등)은
 *   spring.batch.jdbc.initialize-schema 설정에 따라 자동 생성
 *
 * 주의: @EnableBatchProcessing을 사용하면 Spring Boot의 자동 구성이 비활성화되어
 *       스키마 초기화가 동작하지 않습니다. 따라서 자동 구성에 의존합니다.
 */
@Configuration
public class BatchConfig {
    // Spring Boot 자동 구성 사용
    // 필요한 경우 여기에 커스텀 Job, Step 빈을 정의합니다.
}
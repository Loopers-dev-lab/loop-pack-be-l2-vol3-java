package com.loopers.batch.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 배치 전용 JPA 설정.
 * 배치 모듈의 Entity와 Repository를 스캔.
 */
@Configuration
@EntityScan(basePackages = "com.loopers.batch.persistence")
@EnableJpaRepositories(basePackages = "com.loopers.batch.persistence")
public class BatchJpaConfig {
}

package com.loopers.config;

import com.loopers.domain.queue.QueueProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 대기열 설정
 *
 * QueueProperties를 활성화하여 application.yml의 queue.* 속성을 바인딩한다.
 */
@Configuration
@EnableConfigurationProperties(QueueProperties.class)
public class QueueConfig {
}

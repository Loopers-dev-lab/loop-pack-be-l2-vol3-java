package com.loopers.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

@EnableFeignClients(basePackages = "com.loopers.infrastructure")
@Configuration
public class FeignConfig {
}

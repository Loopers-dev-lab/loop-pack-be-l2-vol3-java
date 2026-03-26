package com.loopers.infrastructure.payment;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Profile("!test")
@Configuration
@EnableFeignClients(basePackages = "com.loopers.infrastructure.payment")
public class FeignClientConfiguration {
}
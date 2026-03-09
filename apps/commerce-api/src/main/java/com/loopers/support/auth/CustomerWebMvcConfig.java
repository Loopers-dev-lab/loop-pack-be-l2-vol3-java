package com.loopers.support.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 고객 API 인증 인터셉터 적용 (02 §0, 01 §4.2).
 * /api/v1/** 에 적용하고, 회원가입·상품 조회 등 비회원 허용 경로만 제외. users/me(마이페이지·포인트)는 제외하지 않음.
 */
@Configuration
public class CustomerWebMvcConfig implements WebMvcConfigurer {

    private final CustomerAuthInterceptor customerAuthInterceptor;

    public CustomerWebMvcConfig(CustomerAuthInterceptor customerAuthInterceptor) {
        this.customerAuthInterceptor = customerAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(customerAuthInterceptor)
            .addPathPatterns("/api/v1/**")
            .excludePathPatterns(
                "/api/v1/products/**",
                "/api/v1/users",
                "/api/v1/examples/**"
            );
    }
}

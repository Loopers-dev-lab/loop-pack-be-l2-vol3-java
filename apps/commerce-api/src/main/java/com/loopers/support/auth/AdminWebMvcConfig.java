package com.loopers.support.auth;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
ㄱㄷ * 어드민 API 경로에 인증 인터셉터 등록.
 * {@code /api-admin/**} 전 구간에 {@link AdminAuthInterceptor} 적용 (02 §0). 서명 검증 실패 시 401.
 */
@Configuration
public class AdminWebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    public AdminWebMvcConfig(AdminAuthInterceptor adminAuthInterceptor) {
        this.adminAuthInterceptor = adminAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns("/api-admin/**");
    }
}

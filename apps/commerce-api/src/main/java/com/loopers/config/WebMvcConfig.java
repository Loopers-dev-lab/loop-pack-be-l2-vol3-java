package com.loopers.config;

import com.loopers.interfaces.interceptor.AdminAuthInterceptor;
import com.loopers.interfaces.interceptor.AuthInterceptor;
import com.loopers.interfaces.resolver.LoginUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final AdminAuthInterceptor adminAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/users/signup")
                // BR-A03: 상품 조회 및 브랜드 조회는 인증 없이 접근 가능 (비회원/회원 모두 허용)
                .excludePathPatterns("/api/v1/products/**")
                .excludePathPatterns("/api/v1/brands/**")
                // PG 시스템이 호출하는 콜백 엔드포인트 — 사용자 인증 불가
                .excludePathPatterns("/api/v1/payments/callback");

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api-admin/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }
}

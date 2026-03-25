package com.loopers.interfaces.config;

import com.loopers.interfaces.api.auth.LoginUserArgumentResolver;
import com.loopers.interfaces.api.auth.LoginUserInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@RequiredArgsConstructor
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginUserInterceptor loginUserInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginUserInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                    "/api/v1/users",                    // POST: 회원가입
                    "/api/v1/brands/**",                // GET: 브랜드 조회
                    "/api/v1/products",                 // GET: 상품 목록
                    "/api/v1/products/*",               // GET: 상품 상세 (* 는 '/' 미포함 → /likes 는 여전히 인터셉터 적용)
                    "/api/v1/examples/**",              // GET: 예시
                    "/api/v1/payments/callback"         // POST: PG 결제 콜백 (PG → 서버, 사용자 인증 불필요)
                );
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new LoginUserArgumentResolver());
    }
}

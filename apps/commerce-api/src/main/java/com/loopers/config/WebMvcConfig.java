package com.loopers.config;

import com.loopers.interfaces.api.auth.AdminAuthInterceptor;
import com.loopers.interfaces.api.auth.LoginAdminArgumentResolver;
import com.loopers.interfaces.api.auth.LoginMemberArgumentResolver;
import com.loopers.interfaces.api.auth.MemberAuthInterceptor;
import com.loopers.interfaces.api.queue.QueueTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final MemberAuthInterceptor memberAuthInterceptor;
    private final LoginMemberArgumentResolver loginMemberArgumentResolver;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final LoginAdminArgumentResolver loginAdminArgumentResolver;

    // @WebMvcTest 환경에서는 대기열 빈이 로드되지 않으므로 optional 주입
    @Autowired(required = false)
    private QueueTokenInterceptor queueTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(memberAuthInterceptor)
                .addPathPatterns("/api/v1/**", "/api/v2/**")
                .excludePathPatterns(
                        "/api/v1/members/signup",
                        "/api/v1/brands/**",
                        "/api/v1/products",
                        "/api/v1/products/local-cache",
                        "/api/v1/products/{id}",
                        "/api/v1/products/{id}/local-cache",
                        "/api/v1/products/no-cache",
                        "/api/v1/products/{id}/no-cache",
                        "/api/v1/examples/**",
                        "/api/v1/payments/callback",
                        "/api/v2/payments/callback"
                );

        // 주문 API에 대기열 토큰 검증 인터셉터를 등록한다.
        // MemberAuthInterceptor 이후에 등록되어, 인증된 유저의 토큰만 검증한다.
        if (queueTokenInterceptor != null) {
            registry.addInterceptor(queueTokenInterceptor)
                    .addPathPatterns("/api/v1/orders");
        }

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api-admin/v1/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginMemberArgumentResolver);
        resolvers.add(loginAdminArgumentResolver);
    }
}

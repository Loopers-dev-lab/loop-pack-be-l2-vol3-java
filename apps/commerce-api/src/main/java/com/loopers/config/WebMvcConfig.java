package com.loopers.config;

import com.loopers.interfaces.api.queue.QueueTokenInterceptor;
import com.loopers.support.auth.AdminAuthResolver;
import com.loopers.support.auth.AuthUserResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC 설정
 *
 * 커스텀 ArgumentResolver를 등록하여
 * {@code @AuthUser}, {@code @AuthAdmin} 어노테이션 기반 인증을 활성화한다.
 *
 * QueueTokenInterceptor를 등록하여
 * 주문 API 경로에 대해 대기열 토큰 검증을 수행한다.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthUserResolver authUserResolver;
    private final AdminAuthResolver adminAuthResolver;
    private final QueueTokenInterceptor queueTokenInterceptor;

    public WebMvcConfig(AuthUserResolver authUserResolver, AdminAuthResolver adminAuthResolver,
                        QueueTokenInterceptor queueTokenInterceptor) {
        this.authUserResolver = authUserResolver;
        this.adminAuthResolver = adminAuthResolver;
        this.queueTokenInterceptor = queueTokenInterceptor;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this.authUserResolver);
        resolvers.add(this.adminAuthResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(queueTokenInterceptor)
                .addPathPatterns("/api/v1/orders/**");
    }
}

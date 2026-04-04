package com.loopers.interfaces.api.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthUserArgumentResolver authUserArgumentResolver;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final PgCallbackAuthInterceptor pgCallbackAuthInterceptor;
    private final AuthInterceptor authInterceptor;
    private final EntryTokenInterceptor entryTokenInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authUserArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns("/api-admin/**");
        registry.addInterceptor(pgCallbackAuthInterceptor)
            .addPathPatterns("/api/v1/payments/callback");
        registry.addInterceptor(authInterceptor)
            .order(1)
            .addPathPatterns(
                "/api/v1/orders/**",
                "/api/v1/cart/**",
                "/api/v1/queue/**",
                "/api/v1/likes",
                "/api/v1/products/*/likes",
                "/api/v1/payments",
                "/api/v1/payments/*/sync",
                "/api/v1/payments/orders/*/sync",
                "/api/v1/users/me",
                "/api/v1/users/password"
            );
        registry.addInterceptor(entryTokenInterceptor)
            .order(2)
            .addPathPatterns("/api/v1/orders", "/api/v1/orders/cart");
    }
}

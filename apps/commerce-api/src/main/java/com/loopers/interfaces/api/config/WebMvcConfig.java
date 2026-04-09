package com.loopers.interfaces.api.config;

import java.util.List;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.loopers.interfaces.api.auth.AdminAuthInterceptor;
import com.loopers.interfaces.api.auth.AuthInterceptor;
import com.loopers.interfaces.api.auth.EntryTokenInterceptor;
import com.loopers.interfaces.api.auth.LoginUserArgumentResolver;

import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final EntryTokenInterceptor entryTokenInterceptor;
    private final AdminAuthInterceptor adminAuthInterceptor;
    private final LoginUserArgumentResolver loginUserArgumentResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        authInterceptor.addOptionalPattern("/api/v1/products");
        authInterceptor.addOptionalPattern("/api/v1/products/*");
        authInterceptor.addOptionalPattern("/api/v1/rankings");

        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/users", "/api/v1/brands/**", "/api/v1/payments/callback");

        registry.addInterceptor(entryTokenInterceptor)
                .addPathPatterns("/api/v1/orders");

        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api-admin/**");
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
    }
}

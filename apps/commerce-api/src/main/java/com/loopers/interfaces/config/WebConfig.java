package com.loopers.interfaces.config;

import com.loopers.interfaces.interceptor.EntryTokenInterceptor;
import com.loopers.interfaces.resolver.AdminArgumentResolver;
import com.loopers.interfaces.resolver.LoginUserArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final LoginUserArgumentResolver loginUserArgumentResolver;
    private final AdminArgumentResolver adminArgumentResolver;
    private final EntryTokenInterceptor entryTokenInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(loginUserArgumentResolver);
        resolvers.add(adminArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(entryTokenInterceptor)
                .addPathPatterns("/api/v1/orders", "/api/v1/orders/direct");
    }
}

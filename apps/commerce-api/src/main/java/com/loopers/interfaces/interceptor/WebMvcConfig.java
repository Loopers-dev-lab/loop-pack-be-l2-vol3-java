package com.loopers.interfaces.interceptor;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final QueueTokenInterceptor queueTokenInterceptor;

    @Value("${queue.interceptor.enabled:true}")
    private boolean interceptorEnabled;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (interceptorEnabled) {
            registry.addInterceptor(queueTokenInterceptor)
                    .addPathPatterns("/api/v1/orders");
        }
    }
}

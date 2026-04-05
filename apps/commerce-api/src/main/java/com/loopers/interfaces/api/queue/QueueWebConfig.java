package com.loopers.interfaces.api.queue;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class QueueWebConfig implements WebMvcConfigurer {

    private final EntryTokenInterceptor entryTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(entryTokenInterceptor)
                .addPathPatterns("/api/v1/orders", "/api/v1/orders/**");
    }
}

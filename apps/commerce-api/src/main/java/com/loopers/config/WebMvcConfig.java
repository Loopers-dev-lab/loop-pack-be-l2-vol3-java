package com.loopers.config;

import com.loopers.interfaces.interceptor.EntryTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final EntryTokenInterceptor entryTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(entryTokenInterceptor)
                .addPathPatterns("/api/v1/orders/**", "/api/v1/payments/**");
    }
}

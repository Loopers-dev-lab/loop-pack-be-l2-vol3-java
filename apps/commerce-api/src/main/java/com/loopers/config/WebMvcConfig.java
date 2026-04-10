package com.loopers.config;

import com.loopers.interfaces.api.admin.AdminAuthInterceptor;
import com.loopers.interfaces.api.queue.QueueTokenInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final QueueTokenInterceptor queueTokenInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
            .addPathPatterns("/api-admin/v1/**");

        registry.addInterceptor(queueTokenInterceptor)
            .addPathPatterns("/api/v1/orders/**");
    }
}

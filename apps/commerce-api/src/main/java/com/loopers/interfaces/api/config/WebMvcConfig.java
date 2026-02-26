package com.loopers.interfaces.api.config;

import com.loopers.interfaces.api.interceptor.AdminAuthenticationInterceptor;
import com.loopers.interfaces.api.interceptor.AuthenticationInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthenticationInterceptor authenticationInterceptor;
    private final AdminAuthenticationInterceptor adminAuthenticationInterceptor;

    public WebMvcConfig(AuthenticationInterceptor authenticationInterceptor,
                        AdminAuthenticationInterceptor adminAuthenticationInterceptor) {
        this.authenticationInterceptor = authenticationInterceptor;
        this.adminAuthenticationInterceptor = adminAuthenticationInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authenticationInterceptor)
                .addPathPatterns("/api/v1/users/me", "/api/v1/users/me/**")
                .addPathPatterns("/api/v1/users/*/likes")
                .addPathPatterns("/api/v1/products/*/likes")
                .addPathPatterns("/api/v1/orders", "/api/v1/orders/**");

        registry.addInterceptor(adminAuthenticationInterceptor)
                .addPathPatterns("/api-admin/v1/**");
    }
}

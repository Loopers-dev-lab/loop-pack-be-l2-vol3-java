package com.loopers.config;

import com.loopers.support.auth.AuthUserResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthUserResolver authUserResolver;

    public WebMvcConfig(AuthUserResolver authUserResolver) {
        this.authUserResolver = authUserResolver;
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(this.authUserResolver);
    }
}

package com.loopers.config;

import com.loopers.interfaces.api.AuthUserArgumentResolver;
import com.loopers.interfaces.api.CredentialsHeadersArgumentResolver;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final CredentialsHeadersArgumentResolver credentialsHeadersArgumentResolver;
    private final AuthUserArgumentResolver authUserArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(credentialsHeadersArgumentResolver);
        resolvers.add(authUserArgumentResolver);
    }
}

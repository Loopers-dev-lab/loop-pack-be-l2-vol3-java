package com.loopers.config;

import com.loopers.support.auth.AuthUserResolver;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC 설정
 *
 * {@link AuthUserResolver}를 ArgumentResolver로 등록하여
 * {@code @AuthUser} 어노테이션 기반의 인증된 사용자 주입을 활성화한다.
 */
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

package com.loopers.interfaces.config;

import com.loopers.interfaces.api.AuthUserArgumentResolver;
import com.loopers.interfaces.api.CustomerAuthInterceptor;
import com.loopers.interfaces.apiadmin.AdminAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Spring MVC 설정 클래스.
 *
 * <p>인터셉터 등록 등 웹 계층의 공통 설정을 담당한다.
 * 관리자 API({@code /api-admin/**}) 경로와 고객 API 인증 경로에 대한 인터셉터를 등록한다.</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final CustomerAuthInterceptor customerAuthInterceptor;
    private final AuthUserArgumentResolver authUserArgumentResolver;

    /**
     * 인터셉터를 등록한다.
     *
     * <p>관리자 API 경로({@code /api-admin/**})에 {@link AdminAuthInterceptor}를 적용하고,
     * 고객 인증 필요 API 경로에 {@link CustomerAuthInterceptor}를 적용한다.</p>
     *
     * @param registry 인터셉터 레지스트리
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api-admin/**");

        registry.addInterceptor(customerAuthInterceptor)
                .addPathPatterns("/api/v1/users/me/**")
                .addPathPatterns("/api/v1/products/*/likes")
                .addPathPatterns("/api/v1/cart/**")
                .addPathPatterns("/api/v1/orders/**")
                .addPathPatterns("/api/v1/coupons/**")
                .addPathPatterns("/api/v1/payments/**")
                .excludePathPatterns("/api/v1/payments/callback");
    }

    /**
     * ArgumentResolver를 등록한다.
     *
     * <p>{@link AuthUserArgumentResolver}를 등록하여 {@code @AuthUser} 어노테이션을 지원한다.</p>
     *
     * @param resolvers ArgumentResolver 목록
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authUserArgumentResolver);
    }
}

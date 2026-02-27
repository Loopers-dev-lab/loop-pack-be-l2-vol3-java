package com.loopers.interfaces.config;

import com.loopers.interfaces.apiadmin.AdminAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC 설정 클래스.
 *
 * <p>인터셉터 등록 등 웹 계층의 공통 설정을 담당한다.
 * 관리자 API({@code /api-admin/**}) 경로에 대한 인증 인터셉터를 등록한다.</p>
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;

    /**
     * 인터셉터를 등록한다.
     *
     * <p>관리자 API 경로({@code /api-admin/**})에 {@link AdminAuthInterceptor}를 적용한다.</p>
     *
     * @param registry 인터셉터 레지스트리
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api-admin/**");
    }
}

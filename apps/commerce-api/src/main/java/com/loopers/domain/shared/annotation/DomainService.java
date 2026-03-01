package com.loopers.domain.shared.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Component;

/**
 * 도메인 계층의 도메인 서비스를 나타내는 스테레오타입 어노테이션.
 *
 * <p>도메인 서비스는 특정 엔티티나 값 객체에 자연스럽게 속하지 않는 도메인 비즈니스 규칙을 캡슐화한다.
 * 여러 도메인 객체에 걸친 비즈니스 로직이나, 외부 포트(Repository 등)를 활용한
 * 도메인 규칙 검증(예: 중복 검사)을 수행하는 데 사용한다.</p>
 *
 * <p>Spring의 {@link Component @Component}를 포함하므로 컴포넌트 스캔 대상이 된다.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Component
public @interface DomainService {

}
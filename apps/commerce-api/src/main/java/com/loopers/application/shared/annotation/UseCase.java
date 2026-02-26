package com.loopers.application.shared.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.stereotype.Service;

/**
 * 애플리케이션 계층의 유스케이스를 나타내는 스테레오타입 어노테이션.
 *
 * <p>유스케이스는 하나의 사용자 의도(Use Case)에 대응하는 애플리케이션 서비스로,
 * 도메인 서비스와 리포지토리를 조합하여 비즈니스 흐름을 조율하는 역할을 한다.
 * 비즈니스 규칙 자체는 도메인 객체에 위임하고, 유스케이스는 조율(orchestration)만 담당한다.</p>
 *
 * <p>Spring의 {@link Service @Service}를 포함하므로 컴포넌트 스캔 대상이 된다.</p>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Service
public @interface UseCase {

}

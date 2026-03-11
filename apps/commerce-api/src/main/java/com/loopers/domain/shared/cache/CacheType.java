package com.loopers.domain.shared.cache;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

import lombok.Getter;

/**
 * 런타임에 제네릭 타입 정보를 보존하기 위한 Super Type Token.
 *
 * <p>Java의 타입 소거로 인해 {@code Class<T>}만으로는 {@code Page<Product>} 같은
 * 파라미터화된 타입을 표현할 수 없다. 익명 서브클래스를 생성하면 JVM이 상속 관계의
 * 제네릭 정보를 바이트코드에 보존하므로, 이를 리플렉션으로 추출하여 역직렬화에 활용한다.</p>
 *
 * <pre>{@code
 * // 사용 예시
 * private static final CacheType<Page<Product>> PAGE_TYPE = new CacheType<>() {};
 * cacheRepository.get(key, PAGE_TYPE);
 * }</pre>
 *
 * @param <T> 보존할 대상 타입
 * @see CacheRepository
 */
@Getter
public abstract class CacheType<T> {

    private final Type type;

    protected CacheType() {
        Type superClass = getClass().getGenericSuperclass();
        this.type = ((ParameterizedType) superClass).getActualTypeArguments()[0];
    }
}

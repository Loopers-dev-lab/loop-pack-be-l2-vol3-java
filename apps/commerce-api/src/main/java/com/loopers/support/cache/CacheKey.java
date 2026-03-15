package com.loopers.support.cache;

/**
 * 구분자 기반의 범용 캐시 키 생성기.
 *
 * <p>고정 prefix와 가변 세그먼트를 {@code :}로 결합하여 캐시 키를 생성한다.</p>
 *
 * <pre>{@code
 * CacheKey key = new CacheKey("product", "v1", "detail");
 * key.of(123);       // "product:v1:detail:123"
 * key.pattern();     // "product:v1:detail:*"
 * }</pre>
 */
public class CacheKey {

    private static final String DELIMITER = ":";
    private static final String WILDCARD = "*";

    private final String prefix;

    public CacheKey(String... prefixSegments) {
        this.prefix = String.join(DELIMITER, prefixSegments);
    }

    /**
     * prefix에 세그먼트를 이어 붙여 캐시 키를 생성한다.
     *
     * @param segments 가변 세그먼트
     * @return 완성된 캐시 키
     */
    public String of(Object... segments) {
        StringBuilder sb = new StringBuilder(prefix);
        for (Object segment : segments) {
            sb.append(DELIMITER).append(segment);
        }
        return sb.toString();
    }

    /**
     * prefix 하위의 모든 키를 매칭하는 와일드카드 패턴을 반환한다.
     *
     * @return 와일드카드 패턴 (예: {@code "product:v1:detail:*"})
     */
    public String pattern() {
        return prefix + DELIMITER + WILDCARD;
    }
}

package com.loopers.support.cache;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CacheKeyTest {

    @DisplayName("캐시 키를 생성할 때,")
    @Nested
    class Of {

        @DisplayName("세그먼트를 구분자로 결합하여 키를 반환한다.")
        @Test
        void joinsSegmentsWithDelimiter() {
            // arrange
            CacheKey key = new CacheKey("product", "detail", "v1");

            // act
            String result = key.of(123);

            // assert
            assertThat(result).isEqualTo("product:detail:v1:123");
        }

        @DisplayName("여러 세그먼트를 전달하면 모두 결합한다.")
        @Test
        void joinsMultipleSegments() {
            // arrange
            CacheKey key = new CacheKey("product", "list", "v1");

            // act
            String result = key.of("all", "LATEST", 1, 20);

            // assert
            assertThat(result).isEqualTo("product:list:v1:all:LATEST:1:20");
        }

        @DisplayName("세그먼트 없이 호출하면 prefix만 반환한다.")
        @Test
        void returnsPrefixOnly_whenNoSegments() {
            // arrange
            CacheKey key = new CacheKey("product", "detail", "v1");

            // act
            String result = key.of();

            // assert
            assertThat(result).isEqualTo("product:detail:v1");
        }
    }

    @DisplayName("와일드카드 패턴을 생성할 때,")
    @Nested
    class Pattern {

        @DisplayName("prefix 뒤에 :* 를 붙여 반환한다.")
        @Test
        void appendsWildcard() {
            // arrange
            CacheKey key = new CacheKey("product", "list", "v1");

            // act
            String result = key.pattern();

            // assert
            assertThat(result).isEqualTo("product:list:v1:*");
        }
    }
}

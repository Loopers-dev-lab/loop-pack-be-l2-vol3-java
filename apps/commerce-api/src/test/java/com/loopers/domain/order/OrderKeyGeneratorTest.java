package com.loopers.domain.order;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OrderKeyGeneratorTest {

    private final OrderKeyGenerator generator = new OrderKeyGenerator();

    @DisplayName("주문 키를 생성할 때,")
    @Nested
    class Generate {

        @DisplayName("URL-safe Base64 형식의 22자 문자열을 반환한다.")
        @Test
        void returnsUrlSafeBase64String() {
            // act
            String orderKey = generator.generate();

            // assert
            assertThat(orderKey)
                    .hasSize(22)
                    .matches("^[A-Za-z0-9_-]+$");
        }

        @DisplayName("호출할 때마다 서로 다른 키를 생성한다.")
        @Test
        void generatesUniqueKeys() {
            // act
            Set<String> keys = IntStream.range(0, 100)
                    .mapToObj(i -> generator.generate())
                    .collect(Collectors.toSet());

            // assert
            assertThat(keys).hasSize(100);
        }
    }
}

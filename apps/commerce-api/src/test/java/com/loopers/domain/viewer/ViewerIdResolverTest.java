package com.loopers.domain.viewer;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class ViewerIdResolverTest {

    private final ViewerIdResolver resolver = new ViewerIdResolver();

    @Nested
    class viewerId_변환 {

        @Test
        void userId가_있으면_u_접두사_형식으로_반환한다() {
            assertThat(resolver.resolve(123L, "anon-uuid")).isEqualTo("u:123");
        }

        @Test
        void userId가_없고_anonymousId만_있으면_a_접두사_형식으로_반환한다() {
            assertThat(resolver.resolve(null, "anon-uuid")).isEqualTo("a:anon-uuid");
        }

        @Test
        void userId와_anonymousId가_모두_null이면_예외() {
            assertThatThrownBy(() -> resolver.resolve(null, null))
                    .isInstanceOf(IllegalStateException.class);
        }
    }
}

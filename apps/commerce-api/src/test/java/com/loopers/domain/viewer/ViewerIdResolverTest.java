package com.loopers.domain.viewer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViewerIdResolverTest {

    private final ViewerIdResolver resolver = new ViewerIdResolver();

    @Test
    @DisplayName("userId가 있으면 'u:{userId}' 형식으로 반환한다")
    void resolve_loginUser() {
        assertThat(resolver.resolve(123L, "anon-uuid")).isEqualTo("u:123");
    }

    @Test
    @DisplayName("userId가 없고 anonymousId만 있으면 'a:{anonymousId}' 형식으로 반환한다")
    void resolve_anonymous() {
        assertThat(resolver.resolve(null, "anon-uuid")).isEqualTo("a:anon-uuid");
    }

    @Test
    @DisplayName("userId, anonymousId 둘 다 null이면 IllegalStateException")
    void resolve_bothNull_throws() {
        assertThatThrownBy(() -> resolver.resolve(null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}

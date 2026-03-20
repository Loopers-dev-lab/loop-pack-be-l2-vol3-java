package com.loopers.application.product;

import com.loopers.application.product.cache.EvictPublicProductDetailCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PublicProductDetailCacheEvictionWiringTest {

    @Test
    @DisplayName("상품 수정/삭제는 상세 캐시 무효화 대상이다")
    void productMutation_methodsAreAnnotated() {
        Method updateMethod = Arrays.stream(ProductApplicationService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("update"))
                .findFirst()
                .orElseThrow();
        Method deleteMethod = Arrays.stream(ProductApplicationService.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("deleteSoft"))
                .findFirst()
                .orElseThrow();

        assertThat(updateMethod.getAnnotation(EvictPublicProductDetailCache.class)).isNotNull();
        assertThat(deleteMethod.getAnnotation(EvictPublicProductDetailCache.class)).isNotNull();
    }

    @Test
    @DisplayName("좋아요 수 변경도 상세 캐시 무효화 대상이다")
    void likeMutation_methodsAreAnnotated() throws NoSuchMethodException {
        Method increaseMethod = ProductLikeAplicationService.class.getDeclaredMethod("increaseLikeCount", UUID.class);
        Method decreaseMethod = ProductLikeAplicationService.class.getDeclaredMethod("decreaseLikeCount", UUID.class);
        Method decreaseIfPresentMethod = ProductLikeAplicationService.class.getDeclaredMethod("decreaseLikeCountIfPresent", UUID.class);

        assertThat(increaseMethod.getAnnotation(EvictPublicProductDetailCache.class)).isNotNull();
        assertThat(decreaseMethod.getAnnotation(EvictPublicProductDetailCache.class)).isNotNull();
        assertThat(decreaseIfPresentMethod.getAnnotation(EvictPublicProductDetailCache.class)).isNotNull();
    }
}

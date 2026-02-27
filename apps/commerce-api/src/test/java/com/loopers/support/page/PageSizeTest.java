package com.loopers.support.page;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PageSizeTest {

    @DisplayName("최대 크기를 제한하여 생성할 때,")
    @Nested
    class WithMaxSize {

        @DisplayName("요청 크기가 최대값 이하이면, 요청 크기가 그대로 적용된다.")
        @Test
        void keepsSizeAsIs_whenSizeIsWithinMax() {
            // act
            PageSize pageSize = PageSize.withMaxSize(0, 50);

            // assert
            assertThat(pageSize.size()).isEqualTo(50);
        }

        @DisplayName("요청 크기가 최대값을 초과하면, 최대값(100)으로 제한된다.")
        @Test
        void limitsSizeToMax_whenSizeExceedsMax() {
            // act
            PageSize pageSize = PageSize.withMaxSize(0, 200);

            // assert
            assertThat(pageSize.size()).isEqualTo(100);
        }
    }
}
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

    @DisplayName("오프셋을 계산할 때,")
    @Nested
    class Offset {

        @DisplayName("page * size를 반환한다.")
        @Test
        void returnsPageTimesSize() {
            // act & assert
            assertThat(new PageSize(0, 20).offset()).isZero();
            assertThat(new PageSize(1, 20).offset()).isEqualTo(20);
            assertThat(new PageSize(3, 10).offset()).isEqualTo(30);
        }
    }
}
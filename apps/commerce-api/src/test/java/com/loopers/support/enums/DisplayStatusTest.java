package com.loopers.support.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DisplayStatus 열거형 테스트")
class DisplayStatusTest {

    @Test
    @DisplayName("ACTIVE, HIDDEN 값이 존재한다")
    void values_ShouldContain_ACTIVE_HIDDEN() {
        assertThat(DisplayStatus.values())
                .containsExactlyInAnyOrder(
                        DisplayStatus.ACTIVE,
                        DisplayStatus.HIDDEN
                );
    }
}

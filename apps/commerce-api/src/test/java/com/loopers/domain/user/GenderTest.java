package com.loopers.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GenderTest {

    @DisplayName("성별을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("잘못된 문자열 형식이면, IllegalArgumentException이 발생한다.")
        @Test
        void create_withInvalidString_shouldFail() {
            // given
            String invalidGender = "INVALID";

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                Gender.from(invalidGender);
            });
        }

        @DisplayName("'MALE' 문자열이면, 정상적으로 생성된다.")
        @Test
        void create_withMaleString_shouldSuccess() {
            // given
            String male = "MALE";

            // when
            Gender gender = Gender.from(male);

            // then
            assertThat(gender).isEqualTo(Gender.MALE);
        }

        @DisplayName("'FEMALE' 문자열이면, 정상적으로 생성된다.")
        @Test
        void create_withFemaleString_shouldSuccess() {
            // given
            String female = "FEMALE";

            // when
            Gender gender = Gender.from(female);

            // then
            assertThat(gender).isEqualTo(Gender.FEMALE);
        }
    }
}

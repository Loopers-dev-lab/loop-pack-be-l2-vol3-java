package com.loopers.domain.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailTest {

    @DisplayName("이메일을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("@가 없으면, IllegalArgumentException이 발생한다.")
        @Test
        void create_withoutAtSign_shouldFail() {
            // given
            String invalidEmail = "invalidemail";

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                new Email(invalidEmail);
            });
        }

        @DisplayName("도메인 형식이 잘못되면, IllegalArgumentException이 발생한다.")
        @Test
        void create_withInvalidDomain_shouldFail() {
            // given
            String invalidEmail = "user@";

            // when & then
            assertThrows(IllegalArgumentException.class, () -> {
                new Email(invalidEmail);
            });
        }

        @DisplayName("올바른 형식(xx@yy.zz)이면, 정상적으로 생성된다.")
        @Test
        void create_withValidFormat_shouldSuccess() {
            // given
            String validEmail = "user@example.com";

            // when
            Email email = new Email(validEmail);

            // then
            assertThat(email.value()).isEqualTo(validEmail);
        }
    }
}

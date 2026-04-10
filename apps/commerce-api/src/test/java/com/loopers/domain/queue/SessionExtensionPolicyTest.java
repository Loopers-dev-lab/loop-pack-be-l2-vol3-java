package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SessionExtensionPolicyTest {

    @Nested
    class 생성 {

        @Test
        void 정책_값이_정상적으로_보존된다() {
            SessionExtensionPolicy policy = new SessionExtensionPolicy(300, 60, 2);

            assertAll(
                    () -> assertThat(policy.hardTtlSeconds()).isEqualTo(300),
                    () -> assertThat(policy.extensionSeconds()).isEqualTo(60),
                    () -> assertThat(policy.maxExtensionsPerMinute()).isEqualTo(2)
            );
        }
    }

    @Nested
    class 불변성 {

        @Test
        void 동일한_값으로_생성하면_동등하다() {
            SessionExtensionPolicy policy1 = new SessionExtensionPolicy(300, 60, 2);
            SessionExtensionPolicy policy2 = new SessionExtensionPolicy(300, 60, 2);

            assertThat(policy1).isEqualTo(policy2);
        }

        @Test
        void 다른_값으로_생성하면_동등하지_않다() {
            SessionExtensionPolicy policy1 = new SessionExtensionPolicy(300, 60, 2);
            SessionExtensionPolicy policy2 = new SessionExtensionPolicy(600, 60, 2);

            assertThat(policy1).isNotEqualTo(policy2);
        }
    }
}

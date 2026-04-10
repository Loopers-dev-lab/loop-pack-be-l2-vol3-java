package com.loopers.domain.queue;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class SessionConsumeResultTest {

    @Nested
    class Lua_결과_변환 {

        @Test
        void 값이_1이면_소비_성공이다() {
            SessionConsumeResult result = SessionConsumeResult.fromLuaResult(1);

            assertThat(result).isEqualTo(SessionConsumeResult.CONSUMED);
        }

        @Test
        void 값이_0이면_이미_소비됨이다() {
            SessionConsumeResult result = SessionConsumeResult.fromLuaResult(0);

            assertThat(result).isEqualTo(SessionConsumeResult.ALREADY_CONSUMED);
        }

        @Test
        void 값이_음수1이면_세션_만료이다() {
            SessionConsumeResult result = SessionConsumeResult.fromLuaResult(-1);

            assertThat(result).isEqualTo(SessionConsumeResult.SESSION_EXPIRED);
        }

        @ParameterizedTest
        @ValueSource(longs = {-2, -100, 2, 99})
        void 예상하지_못한_값이면_세션_만료로_처리한다(long unexpected) {
            SessionConsumeResult result = SessionConsumeResult.fromLuaResult(unexpected);

            assertThat(result).isEqualTo(SessionConsumeResult.SESSION_EXPIRED);
        }
    }
}

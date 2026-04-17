package com.loopers.domain.viewer;

import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class BotDetectorTest {

    private final BotDetector botDetector = new BotDetector();

    @Nested
    class 봇_판별 {

        @Test
        void 정상_브라우저_User_Agent는_봇이_아니다() {
            String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
            assertThat(botDetector.isBot(ua)).isFalse();
        }

        @Test
        void Googlebot_User_Agent는_봇이다() {
            String ua = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
            assertThat(botDetector.isBot(ua)).isTrue();
        }

        @Test
        void 빈_문자열_User_Agent는_봇으로_간주한다() {
            assertThat(botDetector.isBot("")).isTrue();
        }

        @Test
        void null_User_Agent는_봇으로_간주한다() {
            assertThat(botDetector.isBot(null)).isTrue();
        }
    }
}

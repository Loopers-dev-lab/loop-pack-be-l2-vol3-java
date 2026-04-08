package com.loopers.domain.viewer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BotDetectorTest {

    private final BotDetector botDetector = new BotDetector();

    @Test
    @DisplayName("정상 브라우저 User-Agent는 봇이 아니다")
    void normalBrowser_isNotBot() {
        String ua = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        assertThat(botDetector.isBot(ua)).isFalse();
    }

    @Test
    @DisplayName("Googlebot User-Agent는 봇이다")
    void googlebot_isBot() {
        String ua = "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)";
        assertThat(botDetector.isBot(ua)).isTrue();
    }

    @Test
    @DisplayName("빈 문자열 User-Agent는 봇으로 간주한다")
    void empty_isBot() {
        assertThat(botDetector.isBot("")).isTrue();
    }

    @Test
    @DisplayName("null User-Agent는 봇으로 간주한다")
    void nullUa_isBot() {
        assertThat(botDetector.isBot(null)).isTrue();
    }
}

package com.loopers.domain.viewer;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class BotDetector {

    private static final Pattern BOT_PATTERN =
            Pattern.compile(".*(bot|crawler|spider|slurp|googlebot|bingbot|yandex|baidu).*", Pattern.CASE_INSENSITIVE);

    public boolean isBot(String userAgent) {
        if (userAgent == null || userAgent.isEmpty()) {
            return true;
        }
        return BOT_PATTERN.matcher(userAgent).matches();
    }
}

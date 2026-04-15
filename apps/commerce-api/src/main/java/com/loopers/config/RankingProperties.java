package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 랭킹 시스템 설정 속성 (commerce-api 측)
 *
 * commerce-streamer의 RankingProperties와 동일한 키/TTL 설정을 공유한다.
 * API 측에서는 키 prefix와 TTL만 사용하지만, 설정 일관성을 위해 동일한 구조를 유지한다.
 */
@ConfigurationProperties(prefix = "ranking")
public class RankingProperties {

    private String keyPrefix = "ranking:all";
    private int ttlDays = 2;
    private String hourlyKeyPrefix = "ranking:hourly";
    private int hourlyTtlHours = 4;

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public int getTtlDays() {
        return ttlDays;
    }

    public void setTtlDays(int ttlDays) {
        this.ttlDays = ttlDays;
    }

    public String getHourlyKeyPrefix() {
        return hourlyKeyPrefix;
    }

    public void setHourlyKeyPrefix(String hourlyKeyPrefix) {
        this.hourlyKeyPrefix = hourlyKeyPrefix;
    }

    public int getHourlyTtlHours() {
        return hourlyTtlHours;
    }

    public void setHourlyTtlHours(int hourlyTtlHours) {
        this.hourlyTtlHours = hourlyTtlHours;
    }
}

package com.loopers.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 랭킹 시스템 설정 속성
 *
 * 가중치 근거:
 * - view(0.1): 가장 빈번한 이벤트. 스코어 지배 방지를 위해 최저 가중치
 * - like(0.2): 조회보다 능동적 행위. 조회의 2배
 * - unlike(-0.2): 좋아요 취소는 좋아요의 역연산
 * - order(0.6): 구매 결정은 가장 강한 인기 신호. 발제 원문 기준
 *
 * 점수 공식: score += weight × 1 (이벤트당 고정)
 * - order는 quantity가 아닌 order-count 기반 (주문 건당 고정 0.6)
 * - "인기 = 관심의 폭(breadth of interest)" 정의에 기반
 *
 * TTL: 일간 키의 1~2배. 어제 랭킹 조회 + carry-over 대비
 */
@ConfigurationProperties(prefix = "ranking")
public class RankingProperties {

    private Weights weights = new Weights();
    private String keyPrefix = "ranking:all";
    private int ttlDays = 2;
    private String hourlyKeyPrefix = "ranking:hourly";
    private int hourlyTtlHours = 4;

    public Weights getWeights() {
        return weights;
    }

    public void setWeights(Weights weights) {
        this.weights = weights;
    }

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

    public static class Weights {

        private double view = 0.1;
        private double like = 0.2;
        private double unlike = -0.2;
        private double order = 0.6;

        public double getView() {
            return view;
        }

        public void setView(double view) {
            this.view = view;
        }

        public double getLike() {
            return like;
        }

        public void setLike(double like) {
            this.like = like;
        }

        public double getUnlike() {
            return unlike;
        }

        public void setUnlike(double unlike) {
            this.unlike = unlike;
        }

        public double getOrder() {
            return order;
        }

        public void setOrder(double order) {
            this.order = order;
        }
    }
}

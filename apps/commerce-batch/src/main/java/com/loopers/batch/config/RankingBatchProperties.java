package com.loopers.batch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 배치용 랭킹 가중치 설정 — commerce-streamer의 RankingProperties와 동기화 필수.
 *
 * WHY duplication:
 * - commerce-batch는 commerce-streamer에 의존하지 않음 (apps 간 횡단 의존 회피)
 * - shared 모듈 추출은 9주차 리팩터 scope라 이번 과제 범위 밖
 * - ProductMetricsEntity 중복 매핑과 같은 컨벤션
 *
 * TODO: 향후 shared config 모듈로 이동. 가중치 변경 시 양쪽 yml 동시 수정 필요.
 * 참고: apps/commerce-streamer/src/main/java/com/loopers/config/RankingProperties.java
 */
@ConfigurationProperties(prefix = "ranking")
public class RankingBatchProperties {

    private Weights weights = new Weights();

    public Weights getWeights() { return weights; }
    public void setWeights(Weights weights) { this.weights = weights; }

    public static class Weights {
        private double view = 0.1;
        private double like = 0.2;
        private double unlike = -0.2;
        private double order = 0.6;

        public double getView() { return view; }
        public void setView(double view) { this.view = view; }
        public double getLike() { return like; }
        public void setLike(double like) { this.like = like; }
        public double getUnlike() { return unlike; }
        public void setUnlike(double unlike) { this.unlike = unlike; }
        public double getOrder() { return order; }
        public void setOrder(double order) { this.order = order; }
    }
}

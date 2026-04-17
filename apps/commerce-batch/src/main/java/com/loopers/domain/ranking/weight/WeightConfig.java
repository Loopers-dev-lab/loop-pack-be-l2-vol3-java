package com.loopers.domain.ranking.weight;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

/**
 * commerce-api / commerce-streamer 가 공유하는 랭킹 가중치 설정의 배치 측 읽기 모델.
 * 배치는 @BeforeStep 에서 active=true 인 행들만 로드하여 Processor 에서 그룹별 fan-out 에 사용한다.
 */
@Entity
@Table(name = "ranking_weight_config")
@Getter
public class WeightConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_name", nullable = false, unique = true, length = 50)
    private String groupName;

    @Column(name = "w_view", nullable = false)
    private double wView;

    @Column(name = "w_like", nullable = false)
    private double wLike;

    @Column(name = "w_order", nullable = false)
    private double wOrder;

    @Column(name = "traffic_pct", nullable = false)
    private int trafficPct;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected WeightConfig() {
    }

    public WeightConfig(String groupName, double wView, double wLike, double wOrder,
                        int trafficPct, boolean active) {
        this.groupName = groupName;
        this.wView = wView;
        this.wLike = wLike;
        this.wOrder = wOrder;
        this.trafficPct = trafficPct;
        this.active = active;
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }
}

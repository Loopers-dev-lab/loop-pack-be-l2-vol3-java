package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

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

    private WeightConfig(String groupName, double wView, double wLike, double wOrder,
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

    public static WeightConfig create(String groupName, double wView, double wLike, double wOrder,
                                       int trafficPct) {
        return new WeightConfig(groupName, wView, wLike, wOrder, trafficPct, true);
    }

    public static WeightConfig defaultConfig() {
        return new WeightConfig("control", 0.1, 0.2, 0.7, 100, true);
    }

    public void updateWeights(double wView, double wLike, double wOrder, int trafficPct) {
        this.wView = wView;
        this.wLike = wLike;
        this.wOrder = wOrder;
        this.trafficPct = trafficPct;
        this.updatedAt = ZonedDateTime.now();
    }

    public void deactivate() {
        this.active = false;
        this.updatedAt = ZonedDateTime.now();
    }
}

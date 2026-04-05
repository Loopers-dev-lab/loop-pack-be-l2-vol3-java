package com.loopers.domain.queue;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 기능 활성화/비활성화 상태를 DB에서 관리하기 위한 피처 플래그 엔티티.
// featureKey를 유니크 키로 사용하여 특정 기능의 on/off를 런타임에 제어한다. >> (애플리케이션 재배포 없이 DB 값만 변경하여 기능을 토글)
//------------------------------------------
// # 사용하는 곳
// 1) 주문시 대기열 사용 여부 /api/v1/orders
//------------------------------------------
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "feature_flag")
public class FeatureFlag extends BaseEntity {

    // 기능을 식별하는 고유 키 (예: "QUEUE_ENABLED")
    @Column(name = "feature_key", nullable = false, unique = true)
    private String featureKey;

    // 기능의 활성화 여부. true이면 해당 기능이 동작한다.
    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    public FeatureFlag(String featureKey, boolean enabled) {
        this.featureKey = featureKey;
        this.enabled = enabled;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }
}

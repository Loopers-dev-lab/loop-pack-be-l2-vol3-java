package com.loopers.domain.event;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_activity_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserActivityLogModel extends BaseEntity {

    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 50)
    private UserActivityType activityType;

    @Column(name = "target_type", nullable = false, length = 50)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private String targetId;

    @Column(name = "detail", columnDefinition = "TEXT")
    private String detail;

    public UserActivityLogModel(Long userId, UserActivityType activityType, String targetType, String targetId, String detail) {
        this.userId = userId;
        this.activityType = activityType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.detail = detail;
    }
}

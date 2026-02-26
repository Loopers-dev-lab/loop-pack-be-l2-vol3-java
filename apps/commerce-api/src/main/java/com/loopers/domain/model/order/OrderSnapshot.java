package com.loopers.domain.model.order;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderSnapshot {

    private final Long id;
    private final String snapshotData;
    private final LocalDateTime createdAt;

    public static OrderSnapshot create(String snapshotData) {
        if (snapshotData == null || snapshotData.isBlank()) {
            throw new IllegalArgumentException("스냅샷 데이터는 필수입니다.");
        }
        return new OrderSnapshot(null, snapshotData, LocalDateTime.now());
    }

    public static OrderSnapshot reconstitute(Long id, String snapshotData, LocalDateTime createdAt) {
        return new OrderSnapshot(id, snapshotData, createdAt);
    }
}

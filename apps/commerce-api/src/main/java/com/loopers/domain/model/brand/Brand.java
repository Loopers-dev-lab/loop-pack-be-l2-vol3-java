package com.loopers.domain.model.brand;

import com.loopers.domain.model.brand.event.BrandDeletedEvent;
import com.loopers.domain.model.common.AggregateRoot;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class Brand extends AggregateRoot {

    private static final int DESCRIPTION_MAX_LENGTH = 500;

    private final Long id;
    private final BrandName name;
    private final String description;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
    private final LocalDateTime deletedAt;

    private Brand(Long id, BrandName name, String description,
                  LocalDateTime createdAt, LocalDateTime updatedAt, LocalDateTime deletedAt) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.deletedAt = deletedAt;
    }

    public static Brand create(BrandName name, String description) {
        LocalDateTime now = LocalDateTime.now();
        return new Brand(null, name, validateDescription(description), now, now, null);
    }

    public static Brand reconstitute(BrandData data) {
        return new Brand(data.id(), data.name(), data.description(),
                data.createdAt(), data.updatedAt(), data.deletedAt());
    }

    public Brand update(BrandName name, String description) {
        return new Brand(this.id, name, validateDescription(description),
                this.createdAt, LocalDateTime.now(), this.deletedAt);
    }

    public Brand delete() {
        if (isDeleted()) {
            throw new IllegalStateException("이미 삭제된 브랜드입니다.");
        }
        Brand deleted = new Brand(this.id, this.name, this.description,
                this.createdAt, this.updatedAt, LocalDateTime.now());
        deleted.registerEvent(new BrandDeletedEvent(this.id));
        return deleted;
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    private static String validateDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        if (trimmed.length() > DESCRIPTION_MAX_LENGTH) {
            throw new IllegalArgumentException("설명은 " + DESCRIPTION_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return trimmed;
    }
}

package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * 브랜드 도메인 엔티티.
 * Soft delete는 BaseEntity의 deletedAt으로 표현한다.
 *
 * @see com.loopers.domain.BaseEntity#delete()
 * @see com.loopers.domain.BaseEntity#restore()
 */
@Entity
@Table(name = "brand")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class BrandModel extends BaseEntity {

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    private BrandModel(String name) {
        this.name = name;
    }

    /**
     * 유효한 이름으로 브랜드를 생성한다.
     *
     * @param name 브랜드 이름 (null·빈 문자열·공백만 불가)
     * @return 생성된 BrandModel
     * @throws IllegalArgumentException name이 null이거나 비어 있는 경우
     */
    public static BrandModel create(String name) {
        if (name == null) {
            throw new IllegalArgumentException("브랜드 이름은 null일 수 없습니다.");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("브랜드 이름은 비어 있을 수 없습니다.");
        }
        return new BrandModel(name.trim());
    }

    /**
     * 브랜드 이름을 수정한다.
     *
     * @param name 새 이름 (null·빈 문자열·공백만 불가)
     * @throws IllegalArgumentException name이 null이거나 비어 있는 경우
     */
    public void updateName(String name) {
        if (name == null) {
            throw new IllegalArgumentException("브랜드 이름은 null일 수 없습니다.");
        }
        if (name.isBlank()) {
            throw new IllegalArgumentException("브랜드 이름은 비어 있을 수 없습니다.");
        }
        this.name = name.trim();
    }

    /**
     * 삭제 여부를 반환한다. BaseEntity의 deletedAt이 설정되어 있으면 삭제된 상태이다.
     */
    public boolean isDeleted() {
        return getDeletedAt() != null;
    }
}

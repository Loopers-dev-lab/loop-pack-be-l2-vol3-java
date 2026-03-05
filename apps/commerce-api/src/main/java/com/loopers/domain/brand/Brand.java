package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "brand")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Brand extends BaseEntity {

    @Column(nullable = false)
    private String name;

    public Brand(String name) {
        validateName(name);
        this.name = name;
    }

    public void update(String name) {
        validateName(name);
        this.name = name;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "브랜드명은 비어있을 수 없습니다.");
        }
    }
}

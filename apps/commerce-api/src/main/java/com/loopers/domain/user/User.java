package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "point", nullable = false)
    private Long point;

    protected User() {}

    public User(String name, Long point) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 이름은 필수입니다");
        }
        if (point == null || point < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "포인트는 0 이상이어야 합니다");
        }
        this.name = name;
        this.point = point;
    }

    public void deductPoint(long amount) {
        if (amount < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "차감 포인트는 1 이상이어야 합니다");
        }
        if (this.point < amount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "포인트가 부족합니다");
        }
        this.point -= amount;
    }

    public String getName() {
        return name;
    }

    public Long getPoint() {
        return point;
    }
}

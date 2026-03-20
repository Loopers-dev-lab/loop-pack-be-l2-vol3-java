package com.loopers.infrastructure.point;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.point.PointBalance;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "point_balances")
public class PointBalanceEntity extends BaseEntity {

    @Getter
    @Column(name = "member_id", nullable = false, unique = true)
    private String memberId;

    @Getter
    @Column(name = "balance", nullable = false)
    private int balance;

    protected PointBalanceEntity() {
    }

    public PointBalanceEntity(String memberId, int balance) {
        this.memberId = memberId;
        this.balance = balance;
    }

    public static PointBalanceEntity from(PointBalance pointBalance) {
        return new PointBalanceEntity(pointBalance.memberId(), pointBalance.balance());
    }

    public void updateFrom(PointBalance pointBalance) {
        this.balance = pointBalance.balance();
        if (pointBalance.deletedAt() != null) {
            delete();
        }
    }

    public PointBalance toDomain() {
        return new PointBalance(getId(), memberId, balance, getCreatedAt(), getUpdatedAt(), getDeletedAt());
    }
}

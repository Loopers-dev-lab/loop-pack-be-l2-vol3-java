package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "coupons")
@Getter
public class Coupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "total_quantity", nullable = false)
    private Integer totalQuantity;

    protected Coupon() {}

    public Coupon(String name, int totalQuantity) {
        this.name = name;
        this.totalQuantity = totalQuantity;
    }
}

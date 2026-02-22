package com.loopers.infrastructure.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Option;
import com.loopers.infrastructure.common.MoneyEmbeddable;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "options")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OptionJpaEntity extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "name", nullable = false)
    private String name;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "additional_price", nullable = false))
    private MoneyEmbeddable additionalPrice;

    @Column(name = "stock", nullable = false)
    private int stock;

    private OptionJpaEntity(Long productId, String name, MoneyEmbeddable additionalPrice, int stock) {
        this.productId = productId;
        this.name = name;
        this.additionalPrice = additionalPrice;
        this.stock = stock;
    }

    public static OptionJpaEntity from(Option option) {
        return new OptionJpaEntity(
                option.getProductId(),
                option.getName(),
                MoneyEmbeddable.from(option.getAdditionalPrice()),
                option.getStock()
        );
    }

    public Option toDomain() {
        return Option.of(getId(), productId, name, additionalPrice.toDomain(), stock);
    }

    public void update(Option option) {
        this.name = option.getName();
        this.additionalPrice = MoneyEmbeddable.from(option.getAdditionalPrice());
        this.stock = option.getStock();
    }
}

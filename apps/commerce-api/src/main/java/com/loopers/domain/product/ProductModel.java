package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.vo.Price;
import com.loopers.domain.product.vo.ProductId;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.common.vo.RefBrandId;
import com.loopers.domain.product.vo.StockQuantity;
import com.loopers.infrastructure.jpa.converter.PriceConverter;
import com.loopers.infrastructure.jpa.converter.ProductIdConverter;
import com.loopers.infrastructure.jpa.converter.ProductNameConverter;
import com.loopers.infrastructure.jpa.converter.RefBrandIdConverter;
import com.loopers.infrastructure.jpa.converter.StockQuantityConverter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;

@Entity
@Table(
    name = "products",
    indexes = {
        @Index(name = "idx_products_brand_latest", columnList = "ref_brand_id, updated_at DESC, deleted_at"),
        @Index(name = "idx_products_brand_price",  columnList = "ref_brand_id, price, deleted_at")
    }
)
@Getter
public class ProductModel extends BaseEntity {

    @Convert(converter = ProductIdConverter.class)
    @Column(name = "product_id", nullable = false, unique = true, length = 20)
    private ProductId productId;

    @Convert(converter = RefBrandIdConverter.class)
    @Column(name = "ref_brand_id", nullable = false)
    private RefBrandId refBrandId;

    @Convert(converter = ProductNameConverter.class)
    @Column(name = "product_name", nullable = false, length = 100)
    private ProductName productName;

    @Convert(converter = PriceConverter.class)
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private Price price;

    @Convert(converter = StockQuantityConverter.class)
    @Column(name = "stock_quantity", nullable = false)
    private StockQuantity stockQuantity;

    protected ProductModel() {}

    private ProductModel(String productId, Long refBrandId, String productName, BigDecimal price, int stockQuantity) {
        this.productId = new ProductId(productId);
        this.refBrandId = new RefBrandId(refBrandId);
        this.productName = new ProductName(productName);
        this.price = new Price(price);
        this.stockQuantity = new StockQuantity(stockQuantity);
    }

    public static ProductModel create(String productId, Long refBrandId, String productName, BigDecimal price, int stockQuantity) {
        return new ProductModel(productId, refBrandId, productName, price, stockQuantity);
    }

    public void decreaseStock(int quantity) {
        if (this.stockQuantity.value() < quantity) {
            throw new CoreException(ErrorType.CONFLICT, "재고가 부족합니다. 현재 재고: " + this.stockQuantity.value() + ", 요청 수량: " + quantity);
        }
        this.stockQuantity = new StockQuantity(this.stockQuantity.value() - quantity);
    }

    public void increaseStock(int quantity) {
        this.stockQuantity = new StockQuantity(this.stockQuantity.value() + quantity);
    }

    public void update(String productName, BigDecimal price, int stockQuantity) {
        this.productName = new ProductName(productName);
        this.price = new Price(price);
        this.stockQuantity = new StockQuantity(stockQuantity);
    }

    public void markAsDeleted() {
        delete();
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }
}

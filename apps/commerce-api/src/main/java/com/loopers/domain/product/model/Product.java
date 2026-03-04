package com.loopers.domain.product.model;

import com.loopers.domain.product.vo.DisplayStatus;
import com.loopers.domain.product.vo.Money;
import com.loopers.domain.product.vo.ProductName;
import com.loopers.domain.product.vo.Stock;
import lombok.Getter;

@Getter
public class Product {

    private Long id;
    private Long brandId;
    private ProductName name;
    private Money price;
    private Stock stock;
    private DisplayStatus displayStatus;

    private Product(Long brandId, ProductName name, Money price, Stock stock, DisplayStatus displayStatus) {
        this.brandId = brandId;
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.displayStatus = displayStatus;
    }

    public static Product create(Long brandId, ProductCommand.Create command) {
        return new Product(
                brandId,
                new ProductName(command.name()),
                new Money(command.price()),
                new Stock(command.stock()),
                DisplayStatus.DISPLAYING
        );
    }

    public static Product reconstruct(Long id, Long brandId, String name, int price, int stock, DisplayStatus displayStatus) {
        Product product = new Product(
                brandId,
                new ProductName(name),
                new Money(price),
                new Stock(stock),
                displayStatus
        );
        product.id = id;
        return product;
    }

    public void update(ProductCommand.Update command) {
        this.name = new ProductName(command.name());
        this.price = new Money(command.price());
        this.stock = new Stock(command.stock());
        this.displayStatus = command.displayStatus();
    }

    public void decreaseStock(int quantity) {
        this.stock = this.stock.decrease(quantity);
    }

    public void increaseStock(int quantity) {
        this.stock = this.stock.increase(quantity);
    }
}

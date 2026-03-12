package com.loopers.domain.product.dto;

public class ProductCommand {

    public record Register(Long brandId, String name, int price, int stock) {}

    public record Update(String name, int price, int stock) {}

    public record StockDeduction(Long productId, int quantity, int expectedPrice) {}
}

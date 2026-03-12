package com.loopers.application.product.dto;

import com.loopers.domain.product.dto.ProductCommand;

public class ProductCriteria {

    public record Register(Long brandId, String name, int price, int stock) {
        public ProductCommand.Register toCommand() {
            return new ProductCommand.Register(brandId, name, price, stock);
        }
    }

    public record Update(String name, int price, int stock) {
        public ProductCommand.Update toCommand() {
            return new ProductCommand.Update(name, price, stock);
        }
    }
}

package com.loopers.domain.product.model;

import com.loopers.domain.product.vo.DisplayStatus;

public final class ProductCommand {

    private ProductCommand() {}

    public record Create(Long brandId, String name, int price, int stock) {}

    public record Update(String name, int price, int stock, DisplayStatus displayStatus) {}
}

package com.loopers.domain.product;

import org.springframework.test.util.ReflectionTestUtils;

public class ProductFixture {

    public static Product createProduct(Long id) {
        var product = Product.create(new ProductSpec(1L, "상품" + id, "http://example.com/" + id + ".jpg", 10000L, 50L, null));
        ReflectionTestUtils.setField(product, "id", id);
        return product;
    }
}

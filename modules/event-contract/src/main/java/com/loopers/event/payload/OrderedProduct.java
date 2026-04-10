package com.loopers.event.payload;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class OrderedProduct {
    private Long productId;
    private Long price;
    private Integer quantity;
}

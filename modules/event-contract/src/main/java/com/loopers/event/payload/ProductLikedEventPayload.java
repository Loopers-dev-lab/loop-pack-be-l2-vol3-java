package com.loopers.event.payload;

import com.loopers.event.EventPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class ProductLikedEventPayload implements EventPayload {
    private Long productId;
    private Long userId;
}

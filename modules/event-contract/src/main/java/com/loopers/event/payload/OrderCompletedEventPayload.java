package com.loopers.event.payload;

import com.loopers.event.EventPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class OrderCompletedEventPayload implements EventPayload {
    private Long orderId;
    private Long userId;
    private List<Long> productIds;
}

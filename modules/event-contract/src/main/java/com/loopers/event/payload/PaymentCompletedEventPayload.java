package com.loopers.event.payload;

import com.loopers.event.EventPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class PaymentCompletedEventPayload implements EventPayload {
    private Long paymentId;
    private Long orderId;
    private Long userId;
}

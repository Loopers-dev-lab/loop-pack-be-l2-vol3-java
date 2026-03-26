package com.loopers.event.payload;

import com.loopers.event.EventPayload;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor(staticName = "of")
public class CouponIssueRequestedEventPayload implements EventPayload {
    private Long couponIssueRequestId;
    private Long couponId;
    private Long userId;
}

package com.loopers.application.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loopers.domain.event.UserActivityLogModel;
import com.loopers.domain.event.UserActivityType;
import com.loopers.infrastructure.event.UserActivityLogJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserActivityEventListener {

    private final UserActivityLogJpaRepository userActivityLogJpaRepository;
    private final ObjectMapper objectMapper;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderPlaced(AppEvents.OrderPlacedApplicationEvent event) {
        saveLog(event.userId(), UserActivityType.ORDER_PLACED, "ORDER", String.valueOf(event.orderId()), Map.of(
            "items", event.items(),
            "totalAmount", event.totalAmount()
        ));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLikeChanged(AppEvents.ProductLikeChangedApplicationEvent event) {
        UserActivityType type = event.delta() > 0 ? UserActivityType.PRODUCT_LIKE : UserActivityType.PRODUCT_UNLIKE;
        saveLog(event.userId(), type, "PRODUCT", String.valueOf(event.productId()), Map.of("delta", event.delta()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCouponIssueRequested(AppEvents.CouponIssueRequestedApplicationEvent event) {
        saveLog(event.userId(), UserActivityType.COUPON_ISSUE_REQUESTED, "COUPON", String.valueOf(event.couponId()), Map.of(
            "requestId", event.requestId()
        ));
    }

    @EventListener
    public void onProductViewed(AppEvents.ProductViewedApplicationEvent event) {
        saveLog(event.userId(), UserActivityType.PRODUCT_VIEW, "PRODUCT", String.valueOf(event.productId()), Map.of());
    }

    @EventListener
    public void onProductClicked(AppEvents.ProductClickedApplicationEvent event) {
        saveLog(event.userId(), UserActivityType.PRODUCT_CLICK, "PRODUCT", String.valueOf(event.productId()), Map.of());
    }

    private void saveLog(Long userId, UserActivityType type, String targetType, String targetId, Map<String, Object> detail) {
        try {
            String detailJson = objectMapper.writeValueAsString(detail);
            userActivityLogJpaRepository.save(new UserActivityLogModel(userId, type, targetType, targetId, detailJson));
        } catch (JsonProcessingException e) {
            log.error("Failed to write activity detail. type={}, targetId={}", type, targetId, e);
        }
    }
}

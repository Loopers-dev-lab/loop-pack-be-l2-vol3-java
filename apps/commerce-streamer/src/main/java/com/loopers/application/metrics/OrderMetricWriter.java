package com.loopers.application.metrics;

import com.loopers.application.metrics.OrderMetricProcessor.OrderItemMetric;
import com.loopers.domain.metrics.ProductOrderMetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
public class OrderMetricWriter {

    private final ProductOrderMetricRepository orderMetricRepository;

    @Transactional
    public void upsertAll(LocalDateTime bucketTime, List<OrderItemMetric> items) {
        for (OrderItemMetric item : items) {
            orderMetricRepository.upsert(
                    item.productId(), bucketTime,
                    1, item.quantity(), item.salesAmount()
            );
        }
    }
}

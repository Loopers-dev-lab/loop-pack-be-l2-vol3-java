package com.loopers.application.product;

import com.loopers.confg.kafka.KafkaTopics;
import com.loopers.domain.outbox.OutboxEventPublisher;
import com.loopers.infrastructure.ranking.RankingCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RequiredArgsConstructor
@Component
public class ProductEventFacade {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ProductFacade productFacade;
    private final OutboxEventPublisher outboxEventPublisher;
    private final RankingCacheService rankingCacheService;

    @Transactional
    public ProductDetailInfo getProductDetail(Long id) {
        outboxEventPublisher.publish(
            KafkaTopics.CATALOG_EVENTS,
            id.toString(),
            "PRODUCT_VIEWED",
            Map.of("productId", id)
        );

        ProductInfo info = productFacade.getProductDetail(id);

        String today = LocalDate.now().format(DATE_FORMATTER);
        Long rank = rankingCacheService.getRank(today, id);

        return ProductDetailInfo.of(info, rank);
    }
}
